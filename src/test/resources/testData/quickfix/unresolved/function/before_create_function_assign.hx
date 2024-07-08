// "Create local function 'getVal'" "true-preview"
class Test {
    function test() {
        var x = getVal<caret>("myStrParam", 10, 2.0);
    }
}