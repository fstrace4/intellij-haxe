class Test {

    public static function main() {
        filter(function(/*<# :String #>*/arg) {return true;});
    }

    public function filter(fn:String -> Bool) {
    }
}