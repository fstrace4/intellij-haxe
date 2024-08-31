package;
import haxe.macro.Expr;

class MacroTest {

    macro public function macroArrayReification(inputArray:Array<String>):ExprOf<Array<String>> {
        return macro $a{ inputArray };
    }

    macro public function macroValueReification():ExprOf<Int> {
        var value = 1;
        return macro { $v{ value } };
    }

    static public function macroBlockReification(a:Int) {
        return macro $b{a};
    }

    static public function macroIdentifierReification(identifier:String) {
        return macro $i{identifier};
    }

    static public function macroFieldReification(field:Array<String>) {
        return macro ($p{field});
    }

    static public function macroExpReification(e:Expr):ExprOf<Void -> Dynamic>  {
        return macro (function() return $e);
    }
}
