package ;
import haxe.Constraints.Function;

enum CommonTypes<T>{
    Array;
    Value(s:T);
}
class Test1147 {
    public function new() {
        // should resolve to normal Array class
        var array:Array<String> = [] ;
        // should use enum values
        var enumValA:CommonTypes<String> = Array;
        var enumValB = CommonTypes.Array;

        var enumValC:Function = CommonTypes.Value;
    }
}
