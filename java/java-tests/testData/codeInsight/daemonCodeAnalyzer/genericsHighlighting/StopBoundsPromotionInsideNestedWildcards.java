interface A<T extends B<? extends T>> { }
interface B<T extends A<?>> { }

class C {
  void foo(A<?> x){
    A<? extends B<? extends A<?>>>  y =  <error descr="Incompatible types. Found: 'A<capture<?>>', required: 'A<? extends B<? extends A<?>>>'">x</error>;
    Object y1 = (A<? extends B<? extends A<?>>>) x;
  }
}