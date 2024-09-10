package ;

typedef  MyDef<T> = {normalVar:String,  typeParamVar:T}

class ResolveFromConstraints<T: Iterable<Int> & MyDef<Array<String>>> {
    var testMember:T;

    public function testClassMember() {
        testMember.<caret>
    }
}

