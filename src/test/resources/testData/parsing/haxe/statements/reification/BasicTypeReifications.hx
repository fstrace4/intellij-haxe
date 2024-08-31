package;

class MacroTest {

    macro public function macroTypeReification() {

        // ComplexType.TPath
        var path:ComplexType = macro : haxe.ds.Map;

        // ComplexType.TFunction
        var func:ComplexType = macro : String -> Int -> Float;

        // ComplexType.TAnonymous
        var anon:ComplexType = macro : {
            field:String
        };

        // ComplexType.TExtend
        var extendedAnon:ComplexType = macro : {
            > Type, field:Type
        };

        // ComplexType.TParent
        var parent:ComplexType = macro : (Type);

        // ComplexType.TOptional
        var optional:ComplexType = macro : ?String;

        //ComplexType.TIntersection
        var intersectionA:ComplexType = macro : {a:String} & {b:String};
        var intersectionB:ComplexType = macro : MacroTest & {};
        var intersectionC:ComplexType = macro : $anon & {};
        var intersectionD:ComplexType = macro : {} & {};

    }


}
