package testData.inlay.haxe.local.variable;
class SimpleVarHints {
    public function new() {
        var fromInitExpression/*<# :String #>*/  = "";
        var fromInitExpressionGenerics/*<# :Array<String> #>*/  = [""];
        var fromFunctionCall/*<# :String #>*/ = simpleFunction();
        var fromVarUsage/*<# :Map<String, Int> #>*/ = new haxe.ds.Map();
        var fromGenerics/*<# :Int #>*/ = genricFunction(1);

        fromVarUsage.set("Key", 1);
    }

    public function  simpleFunction() {
        return "some string";
    }

    public function  genricFunction<T>(x:T):T {
        return x;
    }
}