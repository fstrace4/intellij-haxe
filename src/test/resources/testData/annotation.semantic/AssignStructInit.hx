package;
@:structInit class MyStruct {
  //NOTE: should not show warning about final when in structInit class
  final name:String;
  var age:Int = 30;
  @:optional var address:String;

}

class Test {
  public function new() {
    // correct
    var s1:MyStruct = {name:"name", age:30};
    var s2:MyStruct = {name:"name", age:30, address:"address 1"};

    // wrong (TODO better error messages)
    var <error descr="Incompatible type: {...} should be MyStruct">s3:MyStruct = {name:"name"}</error>; // missing field
    var <error descr="Incompatible type: {...} should be MyStruct">s4:MyStruct = {name:"name", age:30, address:"address 1", extra:"field"}</error>; // to many fields

  }
}