class LooksLikeClass {}

enum EnumA {
    ValueA;
    ValueB;
    ValueC;
}

enum EnumB {
    ValueA;
    ValueB(x:String);
    ValueC(x:Int);

}
// This test makes sure we use the correct EnumValue even though we do not use fully Qualified names
class TestAssignHints {
    public function new() {
        var a:EnumA = null;
        var b:EnumB = null;

        switch (a) {
            case ValueA :trace(a);
            case ValueB :trace(a);
            case ValueC(_) :trace(a);// Wrong: should not have Extracted value
            case var x :
                {
                    // correct type
                    a = x;
                    // wrong type
                    b = <error descr="Incompatible type: EnumA should be EnumB">x</error>;
                }
            default : trace(a);
        }

        switch (b) {
            case ValueA :trace(b);
            case ValueB(_) :trace(b);
            case ValueC :trace(b); // Wrong: should have Extracted value
            case var x :
                {
                    // wrong type
                    a = <error descr="Incompatible type: EnumB should be EnumA">x</error>;
                    // correct type
                    b = x;
                }
            default : trace(b);
        }
    }
}
