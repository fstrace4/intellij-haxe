class Test {

  var member:String;

  public function new() {
    var typeA:{a:String, b:Float};

    // CORRECT
    typeA = {a:"1", b:1.0};
    typeA = {a:"1", b:1.0, extra:"field"};
    typeA = {a:"1", b:1.0, extra:this.member};

    //WRONG: missing "b"
    typeA = <error descr="Incompatible type: missing member(s) b:Float">{a:"1"}</error>;

    //WRONG: incorrect type for "b"
    typeA = {a:"1", b:"Str"};   //TODO: type check temp disabled

  }
}