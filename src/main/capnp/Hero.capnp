@0xb4f31b6201c8558c;

using Java = import "/java.capnp";

$Java.package("connectors");
$Java.outerClassname("HeroOuter");
struct Hero {
  id @0 :UInt64;
  age @1 :UInt16;
  name @2 :Text;
  email @3 :Text;
  address @4 :Text;
}
