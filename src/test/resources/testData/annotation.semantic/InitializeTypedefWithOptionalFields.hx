package;

typedef PtA = {var x:Int; var y:Int; var ?z:Int;};
typedef PtB = {x:Int, ?y:Int, ?z:Int};

class Test {
  static function main() {
    var pa : PtA = {x:1, y:1};
    var pa : PtA = {x:1, y:1, z:1};

    var pb : PtB = {x:1, y:1};
    var pb : PtB = {x:1, y:1, z:1};

    var i : Int = pa.x;
    var j : Int = pb.y;
  }
}