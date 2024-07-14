package;
class FatArrowExpression {
  public function test() {
    var a = map1[0];
    var b:Int = 0;

    //TODO mlo: not sure if any of this makes any sence now?
    // fatArrow expressions are only used in map inits right?
    // these looks more like extractor expressons

    switch a
    {
      // This should parse as a fatArrowExpression.
      case b => _: trace(b);
      case (b => _): trace(b);
      case (b => _)| (b => _): trace(b);
      case b => _| b => _: trace(b);
    }
  }
}