class ResolveTypeFromConstraints<T:Array<String>> {
    var testMember:T;
    public function new() {
        // member
        testMember.indexOf("");
        testMember[0].charAt(1);

        // variable
        var testVariable:T;
        testVariable.indexOf("");
        testVariable[0].charAt(1);

        // variable (no type tag)
        var testNoTypeTag = testMember;
        testNoTypeTag.indexOf("");
        testNoTypeTag[0].charAt(1);
    }
}

typedef  MyDef<T> = {normalVar:String,  typeParamVar:T}

class ResolveFromConstraints<T: Iterable<Int> & MyDef<Array<String>>> {
    var testMember:T;

    public function testClassMember() {

        //CORRECT
        var iterator:Iterator<Int> = testMember.iterator();
        var str:String = testMember.normalVar.charAt(1);
        var index:Int = testMember.typeParamVar.indexOf("");
        var char:String = testMember.typeParamVar[0].charAt(1);

        var typeParamVar:Array<String> = testMember.typeParamVar;
        var iteratorValue:Int = testMember.iterator().next();

        //WRONG (verifing that expressions returns expected types and dont use type hints when resolving)
        var <error descr="Incompatible type: Iterator<Int> should be EnumValue">iterator:EnumValue = testMember.iterator()</error>;
        var <error descr="Incompatible type: String should be EnumValue">str:EnumValue = testMember.normalVar.charAt(1)</error>;
        var <error descr="Incompatible type: Int should be EnumValue">index:EnumValue = testMember.typeParamVar.indexOf("")</error>;
        var <error descr="Incompatible type: String should be EnumValue">char:EnumValue = testMember.typeParamVar[0].charAt(1)</error>;

        var <error descr="Incompatible type: Array<String> should be EnumValue">typeParamVar:EnumValue = testMember.typeParamVar</error>;
        var <error descr="Incompatible type: Int should be EnumValue">iteratorValue:EnumValue = testMember.iterator().next()</error>;

    }
    public function testLocalVar() {

        var testVariable:T;
        var iterator:Iterator<Int> = testVariable.iterator();
        var str:String = testVariable.normalVar.charAt(1);
        var index:Int = testVariable.typeParamVar.indexOf("");
        var char:String = testVariable.typeParamVar[0].charAt(1);

        var typeParamVar:Array<String> = testVariable.typeParamVar;
        var iteratorValue:Int = testVariable.iterator().next();

        //WRONG (verifing that expressions returns expected types and dont use type hints when resolving)
        var <error descr="Incompatible type: Iterator<Int> should be EnumValue">iterator:EnumValue = testVariable.iterator()</error>;
        var <error descr="Incompatible type: String should be EnumValue">str:EnumValue = testVariable.normalVar.charAt(1)</error>;
        var <error descr="Incompatible type: Int should be EnumValue">index:EnumValue = testVariable.typeParamVar.indexOf("")</error>;
        var <error descr="Incompatible type: String should be EnumValue">char:EnumValue = testVariable.typeParamVar[0].charAt(1)</error>;

        var <error descr="Incompatible type: Array<String> should be EnumValue">typeParamVar:EnumValue = testVariable.typeParamVar</error>;
        var <error descr="Incompatible type: Int should be EnumValue">iteratorValue:EnumValue = testVariable.iterator().next()</error>;
    }
}
