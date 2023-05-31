package connectors; /**
 * A simple Spark DataSource V2 reads from s3 csv files.
 */

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.execution.streaming.continuous.RateStreamContinuousReader;
import org.apache.spark.sql.execution.streaming.sources.ForeachWriterCommitMessage$;
import org.apache.spark.sql.execution.streaming.sources.ForeachWriterFactory;
import org.apache.spark.sql.sources.v2.DataSourceOptions;
import org.apache.spark.sql.sources.v2.DataSourceV2;
import org.apache.spark.sql.sources.v2.ReadSupport;
import org.apache.spark.sql.sources.v2.WriteSupport;
import org.apache.spark.sql.sources.v2.reader.DataSourceReader;
import org.apache.spark.sql.sources.v2.reader.streaming.ContinuousReader;
import org.apache.spark.sql.sources.v2.writer.DataSourceWriter;
import org.apache.spark.sql.sources.v2.writer.DataWriter;
import org.apache.spark.sql.sources.v2.writer.DataWriterFactory;
import org.apache.spark.sql.sources.v2.writer.WriterCommitMessage;
import org.apache.spark.sql.types.StructType;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.Optional;


public class SparkConnectorPostgres implements DataSourceV2, ReadSupport, WriteSupport {

    @Override
    public DataSourceReader createReader(DataSourceOptions options) {
        //JdbcDataSourceReader reader = new JdbcDataSourceReader();
        //return new Reader(options);
        // set up the database connection
        /*BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(options.get("http://database-1.cpipa4du110a.us-west-2.rds.amazonaws.com/").get());
        dataSource.setUsername(options.get("postgres").get());
        dataSource.setPassword(options.get("postgres").get());*/

        ContinuousReader reader = new RateStreamContinuousReader(options);
        return reader;
    }

    @Override
    public Optional<DataSourceWriter> createWriter(
        String writeUUID, StructType schema, SaveMode mode, DataSourceOptions options) {
        return Optional.of(new Writer(options));
    }

}

class Writer implements DataSourceWriter {
    private DataSourceOptions options;

    Writer(DataSourceOptions options) {
        this.options = options;
    }

    @Override
    public DataWriterFactory<InternalRow> createWriterFactory() {
        return new JavaSimpleDataWriterFactory(options);
    }

    @Override
    public void commit(WriterCommitMessage[] messages) { }

    @Override
    public void abort(WriterCommitMessage[] messages) { }
}

class JavaSimpleDataWriterFactory implements DataWriterFactory<InternalRow> {
    private String bucket, keyPrefix;

    JavaSimpleDataWriterFactory(DataSourceOptions options) {
        bucket = options.get("bucket").get();
        keyPrefix = options.get("keyPrefix").get();
    }

    @Override
    public DataWriter<InternalRow> createDataWriter(int partitionId, long taskId, long epochId) {
        return new JavaSimpleDataWriter(partitionId, taskId, epochId, bucket, keyPrefix);
    }
}

class JavaSimpleDataWriter implements DataWriter<InternalRow> {
    private int partitionId;
    private long taskId;
    private long epochId;
    private String bucket;
    private String keyPrefix;
    private StringBuilder content = new StringBuilder();
    private AmazonS3 s3Client = AmazonS3Client.builder()
        .withCredentials(new DefaultAWSCredentialsProviderChain())
        .withRegion(new DefaultAwsRegionProviderChain().getRegion())
        .withForceGlobalBucketAccessEnabled(true)
        .build();

    JavaSimpleDataWriter(int partitionId, long taskId, long epochId, String bucket, String keyPrefix) {
        this.partitionId = partitionId;
        this.taskId = taskId;
        this.epochId = epochId;
        this.bucket = bucket;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public void write(InternalRow record) throws IOException {
        org.capnproto.MessageBuilder message = new org.capnproto.MessageBuilder();
        connectors.PersonOuter.Person.Builder personBuilder = message.initRoot(connectors.PersonOuter.Person.factory);
        personBuilder.setId(record.getInt(0));
        personBuilder.setAge((short)record.getInt(1));
        personBuilder.setName(record.getString(2));
        personBuilder.setEmail(record.getString(3));
        org.capnproto.SerializePacked.writeToUnbuffered(
                (new FileOutputStream(FileDescriptor.out)).getChannel(),
                message);
        content.append(message.toString());
    }

    @Override
    public WriterCommitMessage commit() throws IOException {
        s3Client.putObject(bucket,  keyPrefix + "/" + partitionId,  content.toString());
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, keyPrefix + "/" + partitionId);
        request.setExpiration(new Date(System.currentTimeMillis() + 3600000));
        URL preSignedUrl = s3Client.generatePresignedUrl(request);
        //WriterCommitMessage response = ForeachWriterFactory.
        return null;
    }

    @Override
    public void abort() throws IOException { }
}
