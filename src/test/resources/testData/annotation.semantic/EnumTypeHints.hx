class LooksLikeClass {}

enum EnumForHints {
    LooksLikeClass;
    NotClass;
}

enum EnumForExtractorHints<T> {
    ExtractableEnum(x:T);
    ExtractableEnum2(x:T, y:T);
}

class TestAssignHints {
    public function new() {
        // normal assign hinting
        var fullyQualified:EnumForHints = EnumForHints.LooksLikeClass;
        var hintFromType:EnumForHints = NotClass;
        var competingClassName:EnumForHints = LooksLikeClass;
        var className:Class<Dynamic> = LooksLikeClass;

            // not found
        var notFound:EnumForHints = <warning descr="Unresolved symbol">NotFound</warning>;
            // wrong type
        var <error descr="Incompatible type: Class<TestAssignHints> should be EnumForHints">wrong:EnumForHints = TestAssignHints</error>;

        // enum switch extractor hinting

        var str:String;
        var num:Int;
        var flt:Float;

        var enumVarA = ExtractableEnum("StringVal");
        var enumVarB = ExtractableEnum2(1,2);
        switch (enumVarA) {
            case ExtractableEnum(myVal) : str = myVal;
        }

        switch ({a :enumVarA}) {
            case {a : ExtractableEnum(myValA  )} :
            {
                str = myValA; // correct

                    // wrong type
                flt = <error descr="Incompatible type: String should be Float">myValA</error>;
            }
        }

        switch ([enumVarA, enumVarB]) {
            case [ExtractableEnum(myValA), ExtractableEnum(myValB) ]:
            {
                str = myValA; // correct
                num = myValB; // correct

                flt = <error descr="Incompatible type: String should be Float">myValA</error>; // wrong type
            }
        }
        switch ({a :[enumVarA, enumVarB]}) {
            case {a : [ExtractableEnum(myValA ), ExtractableEnum(myValB) ]}:
            {
                str = myValA; // correct
                num = myValB; // correct

                flt = <error descr="Incompatible type: String should be Float">myValA</error>; // wrong type
            }
        }
    }
}
