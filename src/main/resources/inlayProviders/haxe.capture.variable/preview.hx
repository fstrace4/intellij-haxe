enum MyEnum {
    someValue(value:String);
    otherValue;
}

class Test {
    static function main() {
        var myVal = otherValue("myVal", 1);
        switch (myVal) {
            case someValue(value): trace(value);
            /*<# :MyEnum #>*/case var x: trace("enum  is" + x.getName());
        }
    }
}