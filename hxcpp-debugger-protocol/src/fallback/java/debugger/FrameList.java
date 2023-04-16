// Generated by Haxe 4.3.0
package debugger;

import haxe.root.*;

@SuppressWarnings(value={"rawtypes", "unchecked"})
public class FrameList extends haxe.lang.ParamEnum
{
	public FrameList(int index, java.lang.Object[] params)
	{
		//line 240 "C:\\HaxeToolkit\\haxe\\std\\java\\internal\\HxObject.hx"
		super(index, params);
	}
	
	
	public static final java.lang.String[] __hx_constructs = new java.lang.String[]{"Terminator", "Frame"};
	
	public static final debugger.FrameList Terminator = new debugger.FrameList(0, null);
	
	public static debugger.FrameList Frame(boolean isCurrent, int number, java.lang.String className, java.lang.String functionName, java.lang.String fileName, int lineNumber, debugger.FrameList next)
	{
		//line 248 "C:\\HaxeToolkit\\haxe\\lib\\hxcpp-debugger\\git\\debugger\\IController.hx"
		return new debugger.FrameList(1, new java.lang.Object[]{isCurrent, number, className, functionName, fileName, lineNumber, next});
	}
	
	
	@Override public java.lang.String getTag()
	{
		//line 245 "C:\\HaxeToolkit\\haxe\\lib\\hxcpp-debugger\\git\\debugger\\IController.hx"
		return debugger.FrameList.__hx_constructs[this.index];
	}
	
	
}


