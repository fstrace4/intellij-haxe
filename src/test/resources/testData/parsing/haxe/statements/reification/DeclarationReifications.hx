package;
class ReificationsInLoops {

    static function namedClassWithReification(className:String, typeParameter:Null<ComplexType>):TypeDefinition {
        return macro class $className <T:$typeParameter> {
            var member:Array<$typeParameter>;

            public function new() {
                this.member = [];
            }

            public function set(index:Int, value:$typeParameter) {
                member[index] = value;
            }

            public function get(indexi:Int):$typeParameter {
                return member[indexi];
            }

        };
    }

    static function unnamedClassWithReification(className:String, typeParameter:Null<ComplexType>):TypeDefinition {
        var type:TypeDefinition = macro class {
            var member:Array<$typeParameter>;

            public function new() {
            this.member = [];
            }

            public function set(index:Int, value:$typeParameter) {
            member[index] = value;
            }

            public function get(indexi:Int):$typeParameter {
            return member[indexi];
            }

        };
        return type;
    }

    static function namedInterfaceWithReification(interfaceName:String, typeParameter:Null<ComplexType>):TypeDefinition {
        return macro interface $interfaceName {
            public function set(index:Int, value:$typeParameter);

            public function get(indexi:Int):$typeParameter;
        };
    }
}
