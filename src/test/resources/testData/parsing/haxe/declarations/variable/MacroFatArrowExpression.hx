package ;
import haxe.macro.Expr;

class MapBuilder {
    public macro function build(names : Array<String>) : Array<Field> {
        var map : Array<Expr> = [];

        for (name in names) {
            // fat arrow allowed when used in macro expression
            map.push(macro $v{name} => $v{name.length});
        }

        return fields;
    }
}