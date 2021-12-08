package lucee.runtime.helpers;

import java.util.Map;
import java.util.Set;

import lucee.runtime.type.Array;
import lucee.runtime.type.FunctionValueImpl;
import lucee.runtime.type.Struct;

public class ObjectSpread {
    private ObjectSpread() {}

    public static void spreadInto(java.util.ArrayList<FunctionValueImpl> out, Struct runtimeStruct)
    throws Exception
    {
        for (Object rawKey : runtimeStruct.keySet()) {
            if (!(rawKey instanceof String)) { // we could maybe do away with this if Struct impl'd Map<String, Object> instead of raw `Map`, but that may not be feasible 
                throw new Exception("expected a string as a key");
            }

            String key = (String)rawKey;
            Object value = runtimeStruct.get(rawKey);

            // adobe compat
            // foo = {bar: [42]}
            // baz = {...foo} <--- baz == {bar: [42]}, but foo.bar !== baz.bar
            // i.e. object spread makes shallow copies of arrays
            if (value instanceof Array) {
                out.add(new FunctionValueImpl(key, ((Array)value).duplicate(/*deep*/false)));
            }
            else {
                out.add(new FunctionValueImpl(key, value));
            }
        }
    }
}
