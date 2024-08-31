package;
class ReificationsInLoops {

    function forLoopReifications(name:String, values:Array<String>) {
        for ($i{name} in $a{values}) {
            $type($i{name});
        }
    }

    function whileLoopReifications(name:String, value:Int) {
        var $i{name}:Int = 0;
        while ($i{name} < $v{value}) {
            $i{name}++;
        }
    }
    function ArrayInitloopReifications(name:String, values:Array<String>) {
        var myArray = [ for (arg in 0...${values.length}) macro $i { arg } ];
        var myArray = [ for (arg in values) macro $i { arg } ];
    }
}
