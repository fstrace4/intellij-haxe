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
    var a:Int = myObject.myInt;
    var b:String = myObject.myString;
    var c:Float = myObject.StringId;
    var d:String = myObject.nested.subMember;

    var e:String = {someElement:"String"}.someElement;
    var f:Float = {"someElement":1.0}.someElement;
    var g:Float = ({"someElement":0.1}).someElement;
  }
}