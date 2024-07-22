// This test should show no error.
package;
class Test {

  // tests that we can resolve type and members from object literal without type tag
  public var  myObject = {
    myInt:1,
    myString : "myValue",
    "StringId" : 1.0,
    nested:{subMember:""},
    "value.that.can.be.refrenced" : 1
  };

  public function new() {
    var i:Int = myObject.myInt;
    var s:String = myObject.myString;
    var f:Float = myObject.StringId;
    var f:String = myObject.nested.subMember;
  }
}