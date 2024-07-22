package ;

function add(a:Int, b:Int) return a + b;
function mul(a:Int, b:Int) return a * b;
function isEven(value:Float) return value % 2 == 0;


class PatternMachingTest {
    public function testEumVariableCapture() {
        var value = 1;
        switch value {
            // capture variable
            case special = 1:
                trace(special * 2);

            // multiple values
            case 2 | 4 | 6:
                trace("2 or 4 or 6 number");

            // match expression
            case _ * 2 => 10:
                trace("found 5");

            // match expresion with function call
            case add(_, 1) => result:
                trace(result > 1);

            //complex matching expressiom
            case mul(add(_, 1), 3) => result:
                trace(result > 1);

            //chained matching expressiom
            case add(_, 1) => mul(_, 3) => result:
                trace(result > 1);

            // matching expression with capture
            case capture = add(_, 1) => mul(_, 3) => isEven(_) => true:
                trace("even number: " + capture ? "A" : "B");

            // match anything
            case _: trace ("anything");

            // capture variable
            case var other:
                trace("other: " + other);
        }
    }

    public function testSwitchOnArray() {
        var myArray = [1, 6] ;
        switch (myArray) {
            case [2, _]:
                trace("0");
            case [_, 6]:
                trace("1");
            case []:
                trace("2");
            case [_, _, _]:
                trace("3");
            case _:
                trace("4");
        }
    }

    public function  testSwitchOnStructure() {
        var person = { name: "Mark", age: 33 };

        switch person {
            // match person with age older than 50
            case { age: _ > 50 => true}:
                trace('found somebody older than 50');

            // match on specific person named Jose who is 42
            case { name: "Jose", age: 42  }:
                trace('Found Jose, who is 42');

            // match on name
            //TODO mlo: needs resolver work
            case { name: <warning descr="Unresolved symbol">name</warning> }:
                trace('Found someone called $<warning descr="Unresolved symbol">name</warning>');

            // matches anything
            case _:
                trace("unknown");
        }
    }

}