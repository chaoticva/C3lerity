class Test {
  void test(Object o, Integer integer) {
    switch (o) {
      case String s when <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'boolean'">integer</error> -> System.out.println();
      default -> {}
    }

    switch (o) {
      case String s when isBool() -> System.out.println();
      default -> {}
    }

    switch (o) {
      case Integer i when <error descr="Incompatible types. Found: 'int', required: 'boolean'">isInt</error>():
        break;
      default:
        break;
    }

    switch (o) {
      case Integer i when <error descr="Incompatible types. Found: 'null', required: 'boolean'">null</error>:
        break;
      default:
        break;
    }

    boolean flag1;
    switch (o) {
      case Integer i when <error descr="Variable 'flag1' might not have been initialized">flag1</error> -> System.out.println(1);
      default -> System.out.println(0);
    }

    boolean flag2;
    switch (o) {
      case Double d when foo(<error descr="Variable 'flag2' might not have been initialized">flag2</error>) -> System.out.println(2);
      default -> System.out.println(0);
    }
  }

  private native boolean isBool();

  private native int isInt();

  private native boolean foo(boolean blag);

  public static void main2(Object o) {
    int x = switch (o) {
      case Object _, String _ when o == o -> 4;
      case Integer _ -> 2;
      default -> 3;
    };
  }
}