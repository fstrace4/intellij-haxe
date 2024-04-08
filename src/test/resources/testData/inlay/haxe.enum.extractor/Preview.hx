enum Color {
    Red;
    Green;
    Blue;
    Rgb(r:Int, g:Int, b:Int);
}

class Test {
    static function main() {
        var rgb = Color.Rgb(1, 0, 1);
        switch(rgb) {
        /*<# :Int #>*/ ca/*<# :Int #>*/se /*<# :Int #>*/Rgb(r, g, b) :
        trace("R:" + r + " G " + g + " B " + b);
    }
}
}