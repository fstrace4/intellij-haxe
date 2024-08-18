class Test {
    static function main() {
        //RETURN TYPE TESTS
        var voidFnPtr:() -> Void = fnVoid;
        var intFnPtr:() -> Int = fnInt;
        var StrFnPtr:() -> String = fnString;

        // CORRECT: unification allow any return type to be assigned to void return type
        voidFnPtr = fnInt;
        voidFnPtr = fnString;

        // WRONG: if return type is not Void a  unifiyable return type must be provided
        intFnPtr = <error descr="Incompatible type: Void->Void should be Void->Int">fnVoid</error>;
        intFnPtr = <error descr="Incompatible type: Void->String should be Void->Int">fnString</error>;


        var typeFromUsage;
        typeFromUsage = fnInt; // OK: first usage
        typeFromUsage = <error descr="Incompatible type: Void->Void should be Void->Int">voidFnPtr</error>; //WRONG: should fail, for function references we use first usage type

        var localfn = function localFn() return if(true) 1 else 2;
        intFnPtr = localfn; // OK : correct return type
        voidFnPtr = localfn; // OK : void return type signature acepts any return type
        StrFnPtr = <error descr="Incompatible type: Void->Int should be Void->String">localfn</error>; // WRONG:  expects string return, got int

        var localfnA = function localFn() (true) ? fnInt() : fnVoid();// expected void->void
        var localfnB = function localFn() return if (true) fnInt() else fnVoid();//expected void->void
        // OK
        voidFnPtr = localfnA;
        voidFnPtr = localfnB;
        //wrong
        intFnPtr = <error descr="Incompatible type: Void->Void should be Void->Int">localfnA</error>;
        intFnPtr = <error descr="Incompatible type: Void->Void should be Void->Int">localfnB</error>;

        //PARAMETER TESTS
        var parameterFnPtr:(Int) -> Void = fnParamI;// OK: Signature match
        parameterFnPtr = <error descr="Incompatible type: String->Void should be Int->Void">fnParamS</error>; // WRONG : parameters can not be unified;
        voidFnPtr = <error descr="Incompatible type: Int->Void should be Void->Void">fnParamI</error>;//  WRONG: variable type does not have parameters

        // COLLECTION INITIALIZER TESTS WITH if else
        // expect Array<Int>
        var a = [for (i in 0...10) if (i % 2) i];
        //expect Array<Float>
        var b = [for (i in 0...10) if (i % 2 == 0) i else i * 0.5];
        // expect Map<Int, String>
        var c = [for (i in 0...10) if (i % 2 == 0)  i => "even"];
        var d = [for (i in 0...10) if (i % 2 == 0)  i => "even" else i  => "odd"];



    }

    static function fnVoid():Void {return;}
    static function fnInt():Int { return 1; }
    static function fnString():String { return ""; }

    static function fnParamI(i:Int):Void {}
    static function fnParamS(i:String):Void {}

}
