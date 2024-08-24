package ;

enum Test<T> {
    TDoubleVal(s:String, i:Int);
    TString(s:String);
    TInt(i:Int);
    TObject(o:{i:Int, s:String});
    TAny(x:T);
    TTest(t:Test);
    TNone;
}


class PatternMachingTest {
    public function testEumVariableCapture() {
        var myArray = ["String"];
        var  enumVal = Test.TAny(myArray);

        // correct
        switch(enumVal) {
            case TString(x = "s"): x.toLowerCase();
            case TString(_ => _.length => 1):  trace(" 2x '=>' - OK");
            case TString(_ => _.toLowerCase() => "s"):  trace(" 2x '=>' - OK");
            case TNone | TString(_) : trace(" none OR string");
            case TTest(TNone | TString(_)): trace(" sub  OR");
            case TObject( x = {i:1}): x.s.toLowerCase();
            case TString(s): s.toLowerCase();
            case TInt(i): i  * 2;
            case TAny(a): a.indexOf("");
            case TDoubleVal(a, b): a.charAt(b) ;
            case TNone: null;
            case TAny(var x) : trace(x);
            case var value: trace(value);
        }

        var nestedVal = TTest(enumVal);
        switch (nestedVal) {
            case TTest(TString(z)): z.toLowerCase();
        }

        //wrong
        switch(enumVal) {
            case TString(s): <error descr="Unable to apply operator * for types String and Int = 2">s * 2</error>; // WRONG
            case TNone | TString(_): _.toLowerCase(); // TODO, while resolvable this one is not usable as it could be a match on TNone
            case TInt(i): i.<warning descr="Unresolved symbol">length</warning>; // WRONG
            case TAny(a): a.indexOf(<error descr="Type mismatch (Expected: 'String' got: 'Int')">1</error>);
            case TDoubleVal(a, b): b.<warning descr="Unresolved symbol">charAt(a)</warning> ; // WRONG
        }
    }
}