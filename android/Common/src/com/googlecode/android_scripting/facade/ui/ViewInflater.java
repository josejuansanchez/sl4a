package com.googlecode.android_scripting.facade.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.text.method.KeyListener;
import android.text.method.TextKeyListener;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TableLayout;
import android.widget.TextView;

import com.googlecode.android_scripting.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public class ViewInflater {

    public static final String ANDROID = "http://schemas.android.com/apk/res/android";
    public static final int BASESEQ = 0x7f0f0000;
    private static final String DIMENSION = "dim";
    private static final String VIEW_ID = "id";
    private static final String COLOR = "col";

    private static XmlPullParserFactory mFactory;
    private int mNextSeq = BASESEQ;
    private final Map<String, Integer> mIdList = new HashMap<>();
    private final List<String> mErrors = new ArrayList<>();
    private final ViewAttributesHelper attrsHelper = new ViewAttributesHelper();
    private Context mContext;
    private DisplayMetrics mMetrics;
    private static final Map<String, Integer> mInputTypes = new HashMap<>();
    public static final Map<String, String> mColorNames = new HashMap<>();
    public static final Map<String, Integer> mRelative = new HashMap<>();
    private static final Map<String, Map<AttributeInfo, String>> mXmlAttrs = new HashMap<>();
    private Map<Integer, Map<String, String>> mConflictiveAttrs = new HashMap<>();

    private enum AttributeInfo {
        HELPER_METHOD, ATTR_METHOD, VAL_MODIFIER, CONSTANTS
    }

    public static XmlPullParserFactory getFactory() throws XmlPullParserException {
        if (mFactory == null) {
            mFactory = XmlPullParserFactory.newInstance();
            mFactory.setNamespaceAware(true);
        }
        return mFactory;
    }

    public static XmlPullParser getXml() throws XmlPullParserException {
        return getFactory().newPullParser();
    }

    public static XmlPullParser getXml(InputStream is) throws XmlPullParserException {
        XmlPullParser xml = getXml();
        xml.setInput(is, null);
        return xml;
    }

    public static XmlPullParser getXml(Reader ir) throws XmlPullParserException {
        XmlPullParser xml = getXml();
        xml.setInput(ir);
        return xml;
    }

    public View inflate(Activity context, XmlPullParser xml) throws XmlPullParserException,
            IOException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        int event;
        mContext = context;
        mErrors.clear();
        mMetrics = new DisplayMetrics();
        context.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        do {
            event = xml.next();
            if (event == XmlPullParser.END_DOCUMENT) {
                return null;
            }
        } while (event != XmlPullParser.START_TAG);
        return inflateView(context, xml, null);
    }

    private void addLn(Object msg) {
        Log.d(msg.toString());
    }

    @SuppressWarnings("rawtypes")
    public void setClickListener(View v, android.view.View.OnClickListener listener,
                                 OnItemClickListener itemListener, SeekBar.OnSeekBarChangeListener seekBarListener) {
        if (v.isClickable()) {

            if (v instanceof AdapterView) {
                try {
                    ((AdapterView) v).setOnItemClickListener(itemListener);
                } catch (RuntimeException e) {
                    // Ignore this, not all controls support OnItemClickListener
                }
            }
            try {
                v.setOnClickListener(listener);
            } catch (RuntimeException e) {
                // And not all controls support OnClickListener.
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                setClickListener(vg.getChildAt(i), listener, itemListener, seekBarListener);
            }
        }
        if (v instanceof SeekBar) {
            ((SeekBar) v).setOnSeekBarChangeListener(seekBarListener);
        }
    }

    private View inflateView(Context context, XmlPullParser xml, ViewGroup root)
            throws IllegalArgumentException, IllegalAccessException, InvocationTargetException,
            XmlPullParserException, IOException {
        View view = buildView(context, xml, root);
        if (view == null) {
            return null;
        }
        int event;
        while ((event = xml.next()) != XmlPullParser.END_DOCUMENT) {
            switch (event) {
                case XmlPullParser.START_TAG:
                    if (view instanceof ViewGroup) {
                        inflateView(context, xml, (ViewGroup) view);
                    } else {
                        skipTag(xml); // Not really a view, probably, skip it.
                    }
                    break;
                case XmlPullParser.END_TAG:
                    return view;
            }
        }
        return view;
    }

    private void skipTag(XmlPullParser xml) throws XmlPullParserException, IOException {
        int depth = xml.getDepth();
        int event;
        while ((event = xml.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.END_TAG && xml.getDepth() <= depth) {
                break;
            }
        }
    }

    private View buildView(Context context, XmlPullParser xml, ViewGroup root)
            throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {

        View view = viewClass(context, xml.getName());
        if (view != null) {
            getLayoutParams(view, root); // Make quite sure every view has a layout param.
            for (int i = 0; i < xml.getAttributeCount(); i++) {
                String ns = xml.getAttributeNamespace(i);
                String attr = xml.getAttributeName(i);
                if (ANDROID.equals(ns)) {
                    setProperty(view, root, attr, xml.getAttributeValue(i));
                }
            }
            if (root != null) {
                root.addView(view);
            }
        }
        return view;
    }

    private LayoutParams getLayoutParams(View view, ViewGroup root) {
        LayoutParams result = view.getLayoutParams();
        if (result == null) {
            result = createLayoutParams(root);
            view.setLayoutParams(result);
        }
        return result;
    }

    private LayoutParams createLayoutParams(ViewGroup root) {
        LayoutParams result = null;
        if (root != null) {
            try {
                String lookFor = root.getClass().getName() + "$LayoutParams";
                addLn(lookFor);
                Class<? extends LayoutParams> clazz = Class.forName(lookFor).asSubclass(LayoutParams.class);
                if (clazz != null) {
                    Constructor<? extends LayoutParams> ct = clazz.getConstructor(int.class, int.class);
                    result = ct.newInstance(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                }
            } catch (Exception e) {
                result = null;
            }
        }
        if (result == null) {
            result = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }
        return result;
    }

    public void setProperty(View view, String attr, String value) {
        try {
            setProperty(view, (ViewGroup) view.getParent(), attr, value);
        } catch (Exception e) {
            mErrors.add(e.toString());
        }
    }

    private void setProperty(View view, ViewGroup root, String attr, String value)
            throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        addLn(attr + ":" + value);

        if (mXmlAttrs.containsKey(attr)) {
            Map<AttributeInfo, String> propertyInfo = mXmlAttrs.get(attr);

            String info = propertyInfo.get(AttributeInfo.HELPER_METHOD);
            if (info != null) {
                try {
                    Method m;
                    if ((m = tryMethod(attrsHelper, info, View.class, String.class)) != null) {
                        m.invoke(attrsHelper, view, value);
                    } else if ((m = tryMethod(attrsHelper, info, View.class, String.class,
                            String.class)) != null) {
                        m.invoke(attrsHelper, view, attr, value);
                    } else if ((m = tryMethod(attrsHelper, info, View.class, ViewGroup.class,
                            String.class, String.class)) != null) {
                        m.invoke(attrsHelper, view, root, attr, value);
                    }
                    return;
                } catch (Exception e) {
                    addLn(info + ":" + value + ":" + e.toString());
                    mErrors.add(info + ":" + value + ":" + e.toString());
                }
            }
        }

        setDynamicProperty(view, attr, value);
    }

    private void setDynamicProperty (View view, String attr, String value)
            throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        String name = "set" + toPascalCase(attr);
        Class<?> clazz = null;

        if (mXmlAttrs.containsKey(attr)) {
            Map<AttributeInfo, String> propertyInfo = mXmlAttrs.get(attr);
            String info;

            if ((info = propertyInfo.get(AttributeInfo.VAL_MODIFIER)) != null) {
                switch (info) {
                    case DIMENSION:
                        value = "" + attrsHelper.getScaledSize(value);
                        break;
                    case VIEW_ID:
                        value = "" + attrsHelper.calcId(value, false, false);
                        break;
                    case COLOR:
                        value = "" + attrsHelper.getColor(value);
                        break;
                }
            }

            if ((info = propertyInfo.get(AttributeInfo.ATTR_METHOD)) != null) {
                name = info;
            }

            if ((info = propertyInfo.get(AttributeInfo.CONSTANTS)) != null) {
                if (info.startsWith("android.")) {
                    // Init class to get the constants values from it.
                    try {
                        clazz = Class.forName(info);
                    } catch (ClassNotFoundException e) {
                        addLn(name + ":" + value + ":" + e.toString());
                        mErrors.add(name + ":" + value + ":" + e.toString());
                    }
                } else {
                    // Prefix value to help ViewAttributesHelper.getInteger find its eq constant.
                    StringBuilder newValue = new StringBuilder();
                    for (String s : value.split("\\|")) {
                        newValue.append('|');
                        newValue.append(info);
                        newValue.append(s);
                    }
                    newValue.deleteCharAt(0);
                    value = newValue.toString();
                }
            }
        }
        if (mErrors.size() > 0) return;

        try {
            Method m = tryMethod(view, name, CharSequence.class);
            if (m != null) {
                m.invoke(view, value);
            } else if ((m = tryMethod(view, name, Context.class, int.class)) != null) {
                if (clazz == null) clazz = view.getClass();
                m.invoke(view, mContext, attrsHelper.getInteger(clazz, attr, value));
            } else if ((m = tryMethod(view, name, int.class)) != null) {
                if (clazz == null) clazz = view.getClass();
                m.invoke(view, attrsHelper.getInteger(clazz, attr, value));
            } else if ((m = tryMethod(view, name, float.class)) != null) {
                m.invoke(view, Float.parseFloat(value));
            } else if ((m = tryMethod(view, name, boolean.class)) != null) {
                m.invoke(view, Boolean.parseBoolean(value));
            } else if ((m = tryMethod(view, name, Object.class)) != null) {
                m.invoke(view, value);
            } else {
                mErrors.add(view.getClass().getSimpleName() + ":" + attr + " Property not found.");
            }
        } catch (Exception e) {
            addLn(name + ":" + value + ":" + e.toString());
            mErrors.add(name + ":" + value + ":" + e.toString());
        }
    }

    private Method tryMethod(Object o, String name, Class<?>... parameters) {
        Method result;
        try {
            result = o.getClass().getMethod(name, parameters);
        } catch (Exception e) {
            result = null;
        }
        return result;
    }

    private String toPascalCase(String s) {
        if (s == null) {
            return null;
        }
        if (s.length() > 0) {
            return s.substring(0, 1).toUpperCase() + s.substring(1);
        }
        return "";
    }

    private String toCamelCase(String s) {
        if (s == null) {
            return "";
        } else if (s.length() < 2) {
            return s.toUpperCase();
        } else {
            return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
        }
    }

    private String toUnderscore(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        char c;
        for (int i = 0; i < s.length(); i++) {
            c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private View viewClass(Context context, String name) {
        View result;
        result = viewClassTry(context, "android.view." + name);
        if (result == null) {
            result = viewClassTry(context, "android.widget." + name);
        }
        if (result == null) {
            result = viewClassTry(context, name);
        }
        return result;
    }

    private View viewClassTry(Context context, String name) {
        View result = null;
        try {
            Class<? extends View> viewClass = Class.forName(name).asSubclass(View.class);
            if (viewClass != null) {
                Constructor<? extends View> ct = viewClass.getConstructor(Context.class);
                result = ct.newInstance(context);
            }
        } catch (Exception e) {
        }
        return result;

    }

    public Map<String, Integer> getIdList() {
        return mIdList;
    }

    public List<String> getErrors() {
        return mErrors;
    }

    public String getIdName(int id) {
        for (String key : mIdList.keySet()) {
            if (mIdList.get(key) == id) {
                return key;
            }
        }
        return null;
    }

    public int getId(String name) {
        Integer id = mIdList.get(name);
        return (id != null) ? id : 0;
    }

    public Map<String, Map<String, String>> getViewAsMap(View v) {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (Entry<String, Integer> entry : mIdList.entrySet()) {
            View tmp = v.findViewById(entry.getValue());
            if (tmp != null) {
                result.put(entry.getKey(), getViewInfo(tmp));
            }
        }
        return result;
    }

    public Map<String, String> getViewInfo(View v) {
        Map<String, String> result = new HashMap<>();
        if (v.getId() != 0) {
            result.put("id", getIdName(v.getId()));
        }
        result.put("type", v.getClass().getSimpleName());
        addProperty(v, "text", result);
        addProperty(v, "visibility", result);
        addProperty(v, "checked", result);
        addProperty(v, "tag", result);
        addProperty(v, "selectedItemPosition", result);
        addProperty(v, "progress", result);
        return result;
    }

    private void addProperty(View v, String attr, Map<String, String> dest) {
        String result = getProperty(v, attr);
        if (result != null) {
            dest.put(attr, result);
        }
    }

    private String getProperty(View v, String attr) {
        String name = toPascalCase(attr);
        Method m = tryMethod(v, "get" + name);
        if (m == null) {
            m = tryMethod(v, "is" + name);
        }
        String result = null;
        if (m != null) {
            try {
                Object o = m.invoke(v);
                if (o != null) {
                    result = o.toString();
                }
            } catch (Exception e) {
                result = null;
            }
        }
        return result;
    }

    /** Query class (typically R.id) to extract id names */
    public void setIdList(Class<?> idClass) {
        mIdList.clear();
        for (Field f : idClass.getDeclaredFields()) {
            try {
                String name = f.getName();
                int value = f.getInt(null);
                mIdList.put(name, value);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public void setListAdapter(View view, JSONArray items) {
        List<String> list = new ArrayList<>();
        try {
            for (int i = 0; i < items.length(); i++) {
                list.add(items.get(i).toString());
            }
            ArrayAdapter<String> adapter;
            if (view instanceof Spinner) {
                adapter =
                        new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item,
                                android.R.id.text1, list);
            } else {
                adapter =
                        new ArrayAdapter<>(mContext, android.R.layout.simple_list_item_1,
                                android.R.id.text1, list);
            }
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            Method m = tryMethod(view, "setAdapter", SpinnerAdapter.class);
            if (m == null) {
                m = view.getClass().getMethod("setAdapter", ListAdapter.class);
            }
            m.invoke(view, adapter);
        } catch (Exception e) {
            mErrors.add("failed to load list " + e.getMessage());
        }
    }

    public void clearAll() {
        getErrors().clear();
        mIdList.clear();
        mNextSeq = BASESEQ;
    }

    public static Map<String, Integer> getInputTypes() {
        if (mInputTypes.size() == 0) {
            mInputTypes.put("none", 0x00000000);
            mInputTypes.put("text", 0x00000001);
            mInputTypes.put("textCapCharacters", 0x00001001);
            mInputTypes.put("textCapWords", 0x00002001);
            mInputTypes.put("textCapSentences", 0x00004001);
            mInputTypes.put("textAutoCorrect", 0x00008001);
            mInputTypes.put("textAutoComplete", 0x00010001);
            mInputTypes.put("textMultiLine", 0x00020001);
            mInputTypes.put("textImeMultiLine", 0x00040001);
            mInputTypes.put("textNoSuggestions", 0x00080001);
            mInputTypes.put("textUri", 0x00000011);
            mInputTypes.put("textEmailAddress", 0x00000021);
            mInputTypes.put("textEmailSubject", 0x00000031);
            mInputTypes.put("textShortMessage", 0x00000041);
            mInputTypes.put("textLongMessage", 0x00000051);
            mInputTypes.put("textPersonName", 0x00000061);
            mInputTypes.put("textPostalAddress", 0x00000071);
            mInputTypes.put("textPassword", 0x00000081);
            mInputTypes.put("textVisiblePassword", 0x00000091);
            mInputTypes.put("textWebEditText", 0x000000a1);
            mInputTypes.put("textFilter", 0x000000b1);
            mInputTypes.put("textPhonetic", 0x000000c1);
            mInputTypes.put("textWebEmailAddress", 0x000000d1);
            mInputTypes.put("textWebPassword", 0x000000e1);
            mInputTypes.put("number", 0x00000002);
            mInputTypes.put("numberSigned", 0x00001002);
            mInputTypes.put("numberDecimal", 0x00002002);
            mInputTypes.put("numberPassword", 0x00000012);
            mInputTypes.put("phone", 0x00000003);
            mInputTypes.put("datetime", 0x00000004);
            mInputTypes.put("date", 0x00000014);
            mInputTypes.put("time", 0x00000024);
        }
        return mInputTypes;
    }

    static {

        // Support for standard HTML color names.
        mColorNames.put("aliceblue", "#f0f8ff");
        mColorNames.put("antiquewhite", "#faebd7");
        mColorNames.put("aqua", "#00ffff");
        mColorNames.put("aquamarine", "#7fffd4");
        mColorNames.put("azure", "#f0ffff");
        mColorNames.put("beige", "#f5f5dc");
        mColorNames.put("bisque", "#ffe4c4");
        mColorNames.put("black", "#000000");
        mColorNames.put("blanchedalmond", "#ffebcd");
        mColorNames.put("blue", "#0000ff");
        mColorNames.put("blueviolet", "#8a2be2");
        mColorNames.put("brown", "#a52a2a");
        mColorNames.put("burlywood", "#deb887");
        mColorNames.put("cadetblue", "#5f9ea0");
        mColorNames.put("chartreuse", "#7fff00");
        mColorNames.put("chocolate", "#d2691e");
        mColorNames.put("coral", "#ff7f50");
        mColorNames.put("cornflowerblue", "#6495ed");
        mColorNames.put("cornsilk", "#fff8dc");
        mColorNames.put("crimson", "#dc143c");
        mColorNames.put("cyan", "#00ffff");
        mColorNames.put("darkblue", "#00008b");
        mColorNames.put("darkcyan", "#008b8b");
        mColorNames.put("darkgoldenrod", "#b8860b");
        mColorNames.put("darkgray", "#a9a9a9");
        mColorNames.put("darkgrey", "#a9a9a9");
        mColorNames.put("darkgreen", "#006400");
        mColorNames.put("darkkhaki", "#bdb76b");
        mColorNames.put("darkmagenta", "#8b008b");
        mColorNames.put("darkolivegreen", "#556b2f");
        mColorNames.put("darkorange", "#ff8c00");
        mColorNames.put("darkorchid", "#9932cc");
        mColorNames.put("darkred", "#8b0000");
        mColorNames.put("darksalmon", "#e9967a");
        mColorNames.put("darkseagreen", "#8fbc8f");
        mColorNames.put("darkslateblue", "#483d8b");
        mColorNames.put("darkslategray", "#2f4f4f");
        mColorNames.put("darkslategrey", "#2f4f4f");
        mColorNames.put("darkturquoise", "#00ced1");
        mColorNames.put("darkviolet", "#9400d3");
        mColorNames.put("deeppink", "#ff1493");
        mColorNames.put("deepskyblue", "#00bfff");
        mColorNames.put("dimgray", "#696969");
        mColorNames.put("dimgrey", "#696969");
        mColorNames.put("dodgerblue", "#1e90ff");
        mColorNames.put("firebrick", "#b22222");
        mColorNames.put("floralwhite", "#fffaf0");
        mColorNames.put("forestgreen", "#228b22");
        mColorNames.put("fuchsia", "#ff00ff");
        mColorNames.put("gainsboro", "#dcdcdc");
        mColorNames.put("ghostwhite", "#f8f8ff");
        mColorNames.put("gold", "#ffd700");
        mColorNames.put("goldenrod", "#daa520");
        mColorNames.put("gray", "#808080");
        mColorNames.put("grey", "#808080");
        mColorNames.put("green", "#008000");
        mColorNames.put("greenyellow", "#adff2f");
        mColorNames.put("honeydew", "#f0fff0");
        mColorNames.put("hotpink", "#ff69b4");
        mColorNames.put("indianred ", "#cd5c5c");
        mColorNames.put("indigo ", "#4b0082");
        mColorNames.put("ivory", "#fffff0");
        mColorNames.put("khaki", "#f0e68c");
        mColorNames.put("lavender", "#e6e6fa");
        mColorNames.put("lavenderblush", "#fff0f5");
        mColorNames.put("lawngreen", "#7cfc00");
        mColorNames.put("lemonchiffon", "#fffacd");
        mColorNames.put("lightblue", "#add8e6");
        mColorNames.put("lightcoral", "#f08080");
        mColorNames.put("lightcyan", "#e0ffff");
        mColorNames.put("lightgoldenrodyellow", "#fafad2");
        mColorNames.put("lightgray", "#d3d3d3");
        mColorNames.put("lightgrey", "#d3d3d3");
        mColorNames.put("lightgreen", "#90ee90");
        mColorNames.put("lightpink", "#ffb6c1");
        mColorNames.put("lightsalmon", "#ffa07a");
        mColorNames.put("lightseagreen", "#20b2aa");
        mColorNames.put("lightskyblue", "#87cefa");
        mColorNames.put("lightslategray", "#778899");
        mColorNames.put("lightslategrey", "#778899");
        mColorNames.put("lightsteelblue", "#b0c4de");
        mColorNames.put("lightyellow", "#ffffe0");
        mColorNames.put("lime", "#00ff00");
        mColorNames.put("limegreen", "#32cd32");
        mColorNames.put("linen", "#faf0e6");
        mColorNames.put("magenta", "#ff00ff");
        mColorNames.put("maroon", "#800000");
        mColorNames.put("mediumaquamarine", "#66cdaa");
        mColorNames.put("mediumblue", "#0000cd");
        mColorNames.put("mediumorchid", "#ba55d3");
        mColorNames.put("mediumpurple", "#9370d8");
        mColorNames.put("mediumseagreen", "#3cb371");
        mColorNames.put("mediumslateblue", "#7b68ee");
        mColorNames.put("mediumspringgreen", "#00fa9a");
        mColorNames.put("mediumturquoise", "#48d1cc");
        mColorNames.put("mediumvioletred", "#c71585");
        mColorNames.put("midnightblue", "#191970");
        mColorNames.put("mintcream", "#f5fffa");
        mColorNames.put("mistyrose", "#ffe4e1");
        mColorNames.put("moccasin", "#ffe4b5");
        mColorNames.put("navajowhite", "#ffdead");
        mColorNames.put("navy", "#000080");
        mColorNames.put("oldlace", "#fdf5e6");
        mColorNames.put("olive", "#808000");
        mColorNames.put("olivedrab", "#6b8e23");
        mColorNames.put("orange", "#ffa500");
        mColorNames.put("orangered", "#ff4500");
        mColorNames.put("orchid", "#da70d6");
        mColorNames.put("palegoldenrod", "#eee8aa");
        mColorNames.put("palegreen", "#98fb98");
        mColorNames.put("paleturquoise", "#afeeee");
        mColorNames.put("palevioletred", "#d87093");
        mColorNames.put("papayawhip", "#ffefd5");
        mColorNames.put("peachpuff", "#ffdab9");
        mColorNames.put("peru", "#cd853f");
        mColorNames.put("pink", "#ffc0cb");
        mColorNames.put("plum", "#dda0dd");
        mColorNames.put("powderblue", "#b0e0e6");
        mColorNames.put("purple", "#800080");
        mColorNames.put("red", "#ff0000");
        mColorNames.put("rosybrown", "#bc8f8f");
        mColorNames.put("royalblue", "#4169e1");
        mColorNames.put("saddlebrown", "#8b4513");
        mColorNames.put("salmon", "#fa8072");
        mColorNames.put("sandybrown", "#f4a460");
        mColorNames.put("seagreen", "#2e8b57");
        mColorNames.put("seashell", "#fff5ee");
        mColorNames.put("sienna", "#a0522d");
        mColorNames.put("silver", "#c0c0c0");
        mColorNames.put("skyblue", "#87ceeb");
        mColorNames.put("slateblue", "#6a5acd");
        mColorNames.put("slategray", "#708090");
        mColorNames.put("slategrey", "#708090");
        mColorNames.put("snow", "#fffafa");
        mColorNames.put("springgreen", "#00ff7f");
        mColorNames.put("steelblue", "#4682b4");
        mColorNames.put("tan", "#d2b48c");
        mColorNames.put("teal", "#008080");
        mColorNames.put("thistle", "#d8bfd8");
        mColorNames.put("tomato", "#ff6347");
        mColorNames.put("turquoise", "#40e0d0");
        mColorNames.put("violet", "#ee82ee");
        mColorNames.put("wheat", "#f5deb3");
        mColorNames.put("white", "#ffffff");
        mColorNames.put("whitesmoke", "#f5f5f5");
        mColorNames.put("yellow", "#ffff00");
        mColorNames.put("yellowgreen", "#9acd32");

        mRelative.put("above", RelativeLayout.ABOVE);
        mRelative.put("alignBaseline", RelativeLayout.ALIGN_BASELINE);
        mRelative.put("alignBottom", RelativeLayout.ALIGN_BOTTOM);
        mRelative.put("alignLeft", RelativeLayout.ALIGN_LEFT);
        mRelative.put("alignParentBottom", RelativeLayout.ALIGN_PARENT_BOTTOM);
        mRelative.put("alignParentLeft", RelativeLayout.ALIGN_PARENT_LEFT);
        mRelative.put("alignParentRight", RelativeLayout.ALIGN_PARENT_RIGHT);
        mRelative.put("alignParentTop", RelativeLayout.ALIGN_PARENT_TOP);
        mRelative.put("alignRight", RelativeLayout.ALIGN_PARENT_RIGHT);
        mRelative.put("alignTop", RelativeLayout.ALIGN_TOP);
        // mRelative.put("alignWithParentIfMissing",RelativeLayout.); // No idea what this translates to.
        mRelative.put("below", RelativeLayout.BELOW);
        mRelative.put("centerHorizontal", RelativeLayout.CENTER_HORIZONTAL);
        mRelative.put("centerInParent", RelativeLayout.CENTER_IN_PARENT);
        mRelative.put("centerVertical", RelativeLayout.CENTER_VERTICAL);
        mRelative.put("toLeftOf", RelativeLayout.LEFT_OF);
        mRelative.put("toRightOf", RelativeLayout.RIGHT_OF);

        // XML attributes helper HashMap.

        // View
        mXmlAttrs.put("accessibilityTraversalAfter", mapAttrInfo(null, null, VIEW_ID, null));
        mXmlAttrs.put("accessibilityTraversalBefore", mapAttrInfo(null, null, VIEW_ID, null));
        mXmlAttrs.put("background", mapAttrInfo("setBackground", null, null, null));
        mXmlAttrs.put("backgroundTint", mapAttrInfo("setTint", null, null, null));
/*        mXmlAttrs.put("backgroundTintMode", mapAttrInfo("???", null, null, null); // maybe too complex to offer...*/
        mXmlAttrs.put("elevation", mapAttrInfo(null, null, DIMENSION, null));
/*        mXmlAttrs.put("fadeScrollbars", mapAttrInfo(null, "setScrollbarFadingEnabled", null, null); // Crashes the app if view is not scrollable (e.g., ScrollView). I haven't been able to catch the exception.*/
        mXmlAttrs.put("fadingEdgeLength", mapAttrInfo(null, null, DIMENSION, null));
        //mXmlAttrs.put("foreground", RESOURCE);    // waiting to compile project for API 23
        //mXmlAttrs.put("foregroundTint", "setBackgroundTintList," + COLOR_RES); // depends on foreground
        //mXmlAttrs.put("foregroundTintMode", "???"); // depends on foreground
        mXmlAttrs.put("id", mapAttrInfo("setViewId", null, null, null));
        mXmlAttrs.put("isScrollContainer", mapAttrInfo(null, "setScrollContainer", null, null));
/*        mXmlAttrs.put("layerType", mapAttrInfo("???", null, null, null); // maybe too complex to offer...*/
        mXmlAttrs.put("minHeight", mapAttrInfo(null, "setMinimumHeight", DIMENSION, null));
        mXmlAttrs.put("minWidth", mapAttrInfo(null, "setMinimumWidth", DIMENSION, null));
        mXmlAttrs.put("nextFocusDown", mapAttrInfo(null, "setNextFocusDownId", VIEW_ID, null));
        mXmlAttrs.put("nextFocusForward", mapAttrInfo(null, "setNextFocusForwardId", VIEW_ID, null));
        mXmlAttrs.put("nextFocusLeft", mapAttrInfo(null, "setNextFocusLeftId", VIEW_ID, null));
        mXmlAttrs.put("nextFocusRight", mapAttrInfo(null, "setNextFocusRightId", VIEW_ID, null));
        mXmlAttrs.put("nextFocusUp", mapAttrInfo(null, "setNextFocusUpId", VIEW_ID, null));
        mXmlAttrs.put("padding", mapAttrInfo("setPadding", null, null, null));
        mXmlAttrs.put("paddingBottom", mapAttrInfo("setPadding", null, null, null));
        mXmlAttrs.put("paddingEnd", mapAttrInfo("setPadding", null, null, null));
        mXmlAttrs.put("paddingLeft", mapAttrInfo("setPadding", null, null, null));
        mXmlAttrs.put("paddingRight", mapAttrInfo("setPadding", null, null, null));
        mXmlAttrs.put("paddingStart", mapAttrInfo("setPadding", null, null, null));
        mXmlAttrs.put("paddingTop", mapAttrInfo("setPadding", null, null, null));
        mXmlAttrs.put("requiresFadingEdge", mapAttrInfo("setFadingEdge", null, null, null));
        //mXmlAttrs.put("scrollbarIndicator", mapAttrInfo(null, "setScrollIndicators", null, "scroll_indicator_"));  // Test peding for API >= 23.
        mXmlAttrs.put("scrollbarDefaultDelayBeforeFade", mapAttrInfo(null, "setScrollBarDefaultDelayBeforeFade", null, null));
        mXmlAttrs.put("scrollbarFadeDuration", mapAttrInfo(null, "setScrollBarFadeDuration", null, null));
        mXmlAttrs.put("scrollbarSize", mapAttrInfo(null, "setScrollBarSize", DIMENSION, null));
        mXmlAttrs.put("scrollbarStyle", mapAttrInfo(null, "setScrollBarStyle", null, "scrollbars_"));
        mXmlAttrs.put("transformPivotX", mapAttrInfo(null, "setPivotX", DIMENSION, null));
        mXmlAttrs.put("transformPivotY", mapAttrInfo(null, "setPivotY", DIMENSION, null));
        mXmlAttrs.put("translationX", mapAttrInfo(null, null, DIMENSION, null));
        mXmlAttrs.put("translationY", mapAttrInfo(null, null, DIMENSION, null));
        mXmlAttrs.put("translationZ", mapAttrInfo(null, null, DIMENSION, null));

        // ImageView
        mXmlAttrs.put("baseline", mapAttrInfo(null, null, DIMENSION, null));
        mXmlAttrs.put("maxHeight", mapAttrInfo(null, null, DIMENSION, null));
        mXmlAttrs.put("maxWidth", mapAttrInfo(null, null, DIMENSION, null));
        mXmlAttrs.put("src", mapAttrInfo("setImage", null, null, null));
/*        mXmlAttrs.put("scaleType", "con");*/
        mXmlAttrs.put("tint", mapAttrInfo("setTint", null, null, null));
/*        mXmlAttrs.put("tintMode", "setImageTintMode"+"con");*/

        // TextView
/*        mXmlAttrs.put("autoLink", mapAttrInfo(null, "setAutoLinkMask", null, "android.text.util.Linkify"); // it won't work because of constants suffix*/
        mXmlAttrs.put("autoText", mapAttrInfo("setKeyListener", null, null, null)); // conflicts with capitalize.
        mXmlAttrs.put("bufferType", mapAttrInfo("setBufferType", null, null, null)); // conflicts with capitalize.
        mXmlAttrs.put("capitalize", mapAttrInfo("setKeyListener", null, null, null)); // conflicts with autotext.
        mXmlAttrs.put("digits", mapAttrInfo("setKeyListener", null, null, null));
        mXmlAttrs.put("drawableBottom", mapAttrInfo("setCompoundDrawable", null, null, null));
        mXmlAttrs.put("drawableEnd", mapAttrInfo("setCompoundDrawable", null, null, null));
        mXmlAttrs.put("drawableLeft", mapAttrInfo("setCompoundDrawable", null, null, null));
        mXmlAttrs.put("drawablePadding", mapAttrInfo(null, "setCompoundDrawablePadding", DIMENSION, null));
        mXmlAttrs.put("drawableStart", mapAttrInfo("setCompoundDrawable", null, null, null));
        mXmlAttrs.put("drawableRight", mapAttrInfo("setCompoundDrawable", null, null, null));
        //mXmlAttrs.put("drawableTint", mapAttrInfo("setTint", null, null, null));  // API 23
/*        mXmlAttrs.put("drawableTintMode", mapAttrInfo("???", null, null, null); // maybe too complex to offer...*/
        mXmlAttrs.put("drawableTop", mapAttrInfo("setCompoundDrawable", null, null, null));
        mXmlAttrs.put("ellipsize", mapAttrInfo("setEllipsize", null, null, null));
        mXmlAttrs.put("fontFamily", mapAttrInfo("setTypeface", null, null, null));
        mXmlAttrs.put("gravity", mapAttrInfo(null, null, null, "android.view.Gravity"));
        mXmlAttrs.put("height", mapAttrInfo(null, null, DIMENSION, null));
        mXmlAttrs.put("imeActionId", mapAttrInfo("setImeAction", null, null, null));
        mXmlAttrs.put("imeActionLabel", mapAttrInfo("setImeAction", null, null, null));
/*        mXmlAttrs.put("imeOptions", mapAttrInfo(null, null, null, "android.text.inputmethod.EditorInfo"));  // It's in a different class and prefixed... need to think about it.*/
/*        mXmlAttrs.put("inputMethod", mapAttrInfo("setKeyListener", null, null, null));  // haven't figured out how this attr works...*/
        mXmlAttrs.put("inputType", mapAttrInfo(null, null, null, "android.text.InputType"));    // needs revision.
/*        mXmlAttrs.put("lineSpacingExtra", "setLineSpacing");*/
/*        mXmlAttrs.put("lineSpacingMultiplier", "setLineSpacing"); */
/*        mXmlAttrs.put("textAppearance", "res");*/
        mXmlAttrs.put("textColor", mapAttrInfo(null, null, COLOR, null));
        mXmlAttrs.put("textColorHighlight", mapAttrInfo(null, "setHighlightColor", COLOR, null));
        mXmlAttrs.put("textColorHint", mapAttrInfo(null, "setHintTextColor", COLOR, null));
/*        mXmlAttrs.put("textColorLink", mapAttrInfo(null, "setLinkTextColor", COLOR, null));*/
        mXmlAttrs.put("textSize", mapAttrInfo("setTextSize", null, null, null));
        mXmlAttrs.put("textStyle", mapAttrInfo("setTextStyle", null, null, null));
        mXmlAttrs.put("typeface", mapAttrInfo("setTypeface", null, null, null));
        mXmlAttrs.put("width", mapAttrInfo(null, null, DIMENSION, null));

        // ViewGroup.LayoutParams
        mXmlAttrs.put("layout_height", mapAttrInfo("setLayoutProperty", null, null, null));
        mXmlAttrs.put("layout_width", mapAttrInfo("setLayoutProperty", null, null, null));

        // ViewGroup.MarginLayoutParams
        mXmlAttrs.put("layout_marginBottom", mapAttrInfo("setLayoutProperty", null, null, null));
        mXmlAttrs.put("layout_marginLeft", mapAttrInfo("setLayoutProperty", null, null, null));
        mXmlAttrs.put("layout_marginRight", mapAttrInfo("setLayoutProperty", null, null, null));
        mXmlAttrs.put("layout_marginTop", mapAttrInfo("setLayoutProperty", null, null, null));

        // LinearLayout.LayoutParams
        mXmlAttrs.put("layout_gravity", mapAttrInfo("setLayoutProperty", null, null, null));

        // RelativeLayout
        mXmlAttrs.put("ignoreGravity", mapAttrInfo(null, null, VIEW_ID, null));

        // TableLayout
        mXmlAttrs.put("stretchColumns", mapAttrInfo("setStretchColumns", null, null, null));

    }

    private static Map<AttributeInfo, String> mapAttrInfo(String helperMethod, String attrMethod,
                                                          String valModifier, String constants) {
        Map<AttributeInfo, String> infoMap = new EnumMap<>(AttributeInfo.class);
        infoMap.put(AttributeInfo.HELPER_METHOD, helperMethod);
        infoMap.put(AttributeInfo.ATTR_METHOD, attrMethod);
        infoMap.put(AttributeInfo.VAL_MODIFIER, valModifier);
        infoMap.put(AttributeInfo.CONSTANTS, constants);
        return infoMap;
    }

    /**
     * Helper class to set views' attributes.
     *
     * In this class the most problematic attributes are handled by dedicated functions that are
     * called using reflection in {@link ViewInflater#setProperty(View, ViewGroup, String, String)}.
     * Hopefully, most attributes will be handled by generic functions.
     *
     * @author Miguel Palacio (palaciodelgado [at] gmail [dot] com)
     */
    private class ViewAttributesHelper {

        private final int TEXT_VIEW = 0;

        // Dedicated functions

        public void setBackground(View view, String value) {
            if (value.startsWith("#")) {
                view.setBackgroundColor(getColor(value));
            } else if (value.startsWith("@")) {
                setInteger(view, "backgroundResource", getInteger(view, value));
            } else {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    view.setBackgroundDrawable(getDrawable(value));
                } else {
                    view.setBackground(getDrawable(value));
                }
            }
        }

        public void setBufferType(View view, String value) {
            if (!isInstanceOf(view, TEXT_VIEW)) return;

            TextView textView = (TextView) view;
            CharSequence text = textView.getText();
            TextView.BufferType bufferType;
            try {
                bufferType = TextView.BufferType.valueOf(value.toUpperCase());
                textView.setText(text, bufferType);
            } catch (IllegalArgumentException e) {
                mErrors.add("setText:" + value + ":" + e.toString());
            }
        }

        public void setCompoundDrawable(View view, String attr, String value) {
            if (!isInstanceOf(view, TEXT_VIEW)) return;

            TextView textView = (TextView) view;
            Drawable[] dws;
            dws = textView.getCompoundDrawables();
            // So far supports only ColorDrawable and BitmapDrawable.
            Drawable newDrawable;
            if (value.startsWith("#")) {
                newDrawable = new ColorDrawable(getColor(value));
            } else {
                newDrawable = getDrawable(value);
            }
            if (mErrors.size() > 0) return;

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                switch (attr) {
                    case "drawableBottom":
                        textView.setCompoundDrawables(dws[0], dws[1], dws[2], newDrawable);
                        break;
                    case "drawableLeft":
                        textView.setCompoundDrawables(newDrawable, dws[1], dws[2], dws[3]);
                        break;
                    case "drawableRight":
                        textView.setCompoundDrawables(dws[0], dws[1], newDrawable, dws[3]);
                        break;
                    case "drawableTop":
                        textView.setCompoundDrawables(dws[0], newDrawable, dws[2], dws[3]);
                        break;
                    default:
                        mErrors.add("attribute not supported by devices with API < 17");
                }
            } else {
                Drawable[] dwsRel;
                dwsRel = textView.getCompoundDrawablesRelative();
                switch (attr) {
                    case "drawableBottom":
                        textView.setCompoundDrawablesRelative(dwsRel[0], dwsRel[1], dwsRel[2], newDrawable);
                        break;
                    case "drawableEnd":
                        textView.setCompoundDrawablesRelative(dwsRel[0], dwsRel[1], newDrawable, dwsRel[3]);
                        break;
                    case "drawableLeft":
                        textView.setCompoundDrawables(newDrawable, dws[1], dws[2], dws[3]);
                        break;
                    case "drawableRight":
                        textView.setCompoundDrawables(dws[0], dws[1], newDrawable, dws[3]);
                        break;
                    case "drawableStart":
                        textView.setCompoundDrawablesRelative(newDrawable, dwsRel[1], dwsRel[2], dwsRel[3]);
                        break;
                    case "drawableTop":
                        textView.setCompoundDrawablesRelative(dwsRel[0], newDrawable, dwsRel[2], dwsRel[3]);
                        break;
                }
            }
        }

        public void setEllipsize(View view, String value) {
            if (!isInstanceOf(view, TEXT_VIEW)) return;
            try {
                ((TextView) view).setEllipsize(TextUtils.TruncateAt.valueOf(value.toUpperCase()));
            } catch (IllegalArgumentException e) {
                mErrors.add("setEllipsize:" + value + ":" + e.toString());
            }
        }

        public void setFadingEdge(View view, String value) {
            String[] values = value.split("\\|");
            for (String v : values) {
                switch (v.toLowerCase()) {
                    case "none":
                        view.setVerticalFadingEdgeEnabled(false);
                        view.setHorizontalFadingEdgeEnabled(false);
                        break;
                    case "horizontal":
                        view.setHorizontalFadingEdgeEnabled(true);
                        break;
                    case "vertical":
                        view.setVerticalFadingEdgeEnabled(true);
                        break;
                    default:
                        mErrors.add("Unknown value: " + v);
                }
            }
        }

        public void setForeground(View view, String value) {
            Drawable drawable;
            if (value.startsWith("#")) {
                drawable = new ColorDrawable(getColor(value));
            }
            //view.setForeground ... needs to be compiled against API 23
        }

        public void setImage(View view, String value) {
            if (value.startsWith("@")) {
                setInteger(view, "imageResource", getInteger(view, value));
            } else if (value.startsWith("#") && view instanceof ImageView) {
                int color = getColor(value);
                if (color != 0) {
                    ColorDrawable cd = new ColorDrawable(color);
                    ((ImageView) view).setImageBitmap(null);
                    ((ImageView) view).setImageDrawable(cd);
                } else {
                    mErrors.add("color not in the form '#rgb', '#argb', '#rrggbb', '#aarrggbb");
                }
            } else {
                try {
                    Uri uri = Uri.parse(value);
                    if ("file".equals(uri.getScheme())) {
                        Bitmap bm = BitmapFactory.decodeFile(uri.getPath());
                        Method method = view.getClass().getMethod("setImageBitmap", Bitmap.class);
                        method.invoke(view, bm);
                    } else {
                        mErrors.add("Only 'file' currently supported for images");
                    }
                } catch (Exception e) {
                    mErrors.add("failed to set image " + value);
                }
            }
        }

        public void setImeAction(View view, String attr, String value) {
            if (!isInstanceOf(view, TEXT_VIEW)) return;

            TextView textView = (TextView) view;
            if (attr.equals("imeActionId")) {
                CharSequence label = textView.getImeActionLabel();
                textView.setImeActionLabel(label, Integer.parseInt(value));
            } else {
                int id = textView.getImeActionId();
                textView.setImeActionLabel(value, id);
            }
        }

        public void setKeyListener(View view, String attr, String value) {
            if (!isInstanceOf(view, TEXT_VIEW)) return;

            TextView textView = (TextView) view;
            Map<String, String> conflicts;
            int viewId = view.getId();
            switch (attr) {
                case "autoText":
                    if ((conflicts = mConflictiveAttrs.get(viewId)) == null) {
                        conflicts = new HashMap<>();
                    }
                    TextKeyListener.Capitalize cap = TextKeyListener.Capitalize.NONE;
                    if (conflicts.containsKey("capitalize")) {
                        cap = TextKeyListener.Capitalize.valueOf(conflicts.get("capitalize"));
                    }
                    textView.setKeyListener(TextKeyListener.getInstance(
                            Boolean.parseBoolean(value), cap));
                    conflicts.put(attr, value);
                    mConflictiveAttrs.put(viewId, conflicts);
                    break;
                case "capitalize":
                    if ((conflicts = mConflictiveAttrs.get(viewId)) == null) {
                        conflicts = new HashMap<>();
                    }
                    boolean auto = false;
                    if (conflicts.containsKey("autoText")) {
                        auto = Boolean.parseBoolean(conflicts.get("autoText"));
                    }
                    textView.setKeyListener(TextKeyListener.getInstance(
                            auto, TextKeyListener.Capitalize.valueOf(value.toUpperCase())));
                    conflicts.put(attr, value);
                    mConflictiveAttrs.put(viewId, conflicts);
                    break;
                case "digits":
                    textView.setKeyListener(DigitsKeyListener.getInstance(value));
                    break;
            }
        }

        public void setLayoutProperty(View view, ViewGroup root, String attr, String value) {
            LayoutParams layout = getLayoutParams(view, root);
            String layoutAttr = attr.substring(7);
            switch (layoutAttr) {
                case "width":
                    layout.width = getLayoutValue(value);
                    break;
                case "height":
                    layout.height = getLayoutValue(value);
                    break;
                case "gravity":
                    setIntegerField(layout, "gravity", getInteger(Gravity.class, value));
                    break;
                default:
                    if (layoutAttr.startsWith("margin") && layout instanceof MarginLayoutParams) {
                        int size = (int) getScaledSize(value);
                        MarginLayoutParams margins = (MarginLayoutParams) layout;
                        switch (layoutAttr) {
                            case "marginBottom":
                                margins.bottomMargin = size;
                                break;
                            case "marginTop":
                                margins.topMargin = size;
                                break;
                            case "marginLeft":
                                margins.leftMargin = size;
                                break;
                            case "marginRight":
                                margins.rightMargin = size;
                                break;
                        }
                    } else if (layout instanceof RelativeLayout.LayoutParams) {
                        int anchor = calcId(value, false, false);
                        if (anchor == 0) {
                            anchor = getInteger(RelativeLayout.class, value);
                        }
                        int rule = mRelative.get(layoutAttr);
                        ((RelativeLayout.LayoutParams) layout).addRule(rule, anchor);
                    } else {
                        setIntegerField(layout, layoutAttr, getInteger(layout.getClass(), value));
                    }
            }
            view.setLayoutParams(layout);
        }

        public void setPadding(View view, String attr, String value) {
            int newPadding = (int) getScaledSize(value);
            if (attr.equals("padding")) {
                view.setPadding(newPadding, newPadding, newPadding, newPadding);
            } else {
                int bottom, end, left, right, start, top;
                bottom = view.getPaddingBottom();
                top = view.getPaddingTop();
                left = view.getPaddingLeft();
                right = view.getPaddingRight();

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    switch (attr) {
                        case "paddingBottom":
                            view.setPadding(left, top, right, newPadding);
                            break;
                        case "paddingLeft":
                            view.setPadding(newPadding, top, right, bottom);
                            break;
                        case "paddingRight":
                            view.setPadding(left, top, newPadding, bottom);
                            break;
                        case "paddingTop":
                            view.setPadding(left, newPadding, right, bottom);
                            break;
                        default:
                            mErrors.add("attribute not supported by devices with API < 17");
                    }
                } else {
                    start = view.getPaddingStart();
                    end = view.getPaddingEnd();
                    switch (attr) {
                        case "paddingBottom":
                            view.setPaddingRelative(start, top, end, newPadding);
                            break;
                        case "paddingEnd":
                            view.setPaddingRelative(start, top, newPadding, bottom);
                            break;
                        case "paddingLeft":
                            view.setPadding(newPadding, top, right, bottom);
                            break;
                        case "paddingRight":
                            view.setPadding(left, top, newPadding, bottom);
                            break;
                        case "paddingStart":
                            view.setPaddingRelative(newPadding, top, end, bottom);
                            break;
                        case "paddingTop":
                            view.setPaddingRelative(start, newPadding, end, bottom);
                            break;
                    }
                }
            }
        }

        public void setStretchColumns(View view, String value) {
            TableLayout table = (TableLayout) view;
            if (value.trim().equals("*")) {
                table.setStretchAllColumns(true);
                table.requestLayout();
            } else {
                String[] values = value.split(",");
                for (String column : values) {
                    table.setColumnStretchable(Integer.parseInt(column.trim()), true);
                }
            }
        }

        public void setTextSize(View view, String value) {
            if (!isInstanceOf(view, TEXT_VIEW)) return;

            float scaledPixels = getScaledSize(value) / mMetrics.scaledDensity; // convert into "sp"
            ((TextView) view).setTextSize(scaledPixels);
        }

        public void setTextStyle(View view, String value) {
            if (!isInstanceOf(view, TEXT_VIEW)) return;

            TextView textview = (TextView) view;
            int style = getInteger(Typeface.class, value);
            if (style == 0) {
                textview.setTypeface(Typeface.DEFAULT);
            } else {
                textview.setTypeface(textview.getTypeface(), style);
            }
        }

        public void setTint(View view, String attr, String value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ColorStateList tint = null;
                if (!value.equals("null")) tint = ColorStateList.valueOf(getColor(value));
                switch (attr) {
                    case "backgroundTint":
                        view.setBackgroundTintList(tint);
                        break;
                    case "drawableTint":
                        if (!isInstanceOf(view, TEXT_VIEW)) break;
                        //((TextView)view).setCompoundDrawableTintList(tint); API 23
                        break;
                    case "foregroundTint":
                        //view.setForegroundTintList(tint);
                        break;
                    case "tint":
                        if (!isInstanceOf(view, TEXT_VIEW)) break;
                        ((ImageView) view).setImageTintList(tint);
                        break;
                    default:
                        mErrors.add("attribute not found");
                }
            } else {
                mErrors.add("attribute not supported by devices with API < 21");
            }
        }

        public void setTypeface(View view, String value) {
            if (!isInstanceOf(view, TEXT_VIEW)) return;

            TextView textview = (TextView) view;
            Typeface typeface = textview.getTypeface();
            int style = typeface == null ? 0 : typeface.getStyle();
            textview.setTypeface(Typeface.create(value, style));
        }

        public void setViewId(View view, String value) {
            int id = calcId(value, true, view.getId() == View.NO_ID);
            if (id != 0) {
                view.setId(id);
            }
        }

        // Auxiliary functions

        public int calcId(String value, boolean isNewId, boolean isLoadingLayout) {
            if (value == null) {
                return 0;
            }
            int id = 0;
            if (isLoadingLayout) {
                if (value.startsWith("@+id/")) {
                    value = value.substring(5);
                    id = tryGetId(value, true);
                } else if (value.startsWith("@id/")) {
                    isNewId = false;
                    value = value.substring(4);
                    id = tryGetId(value, false);    // id should be in R.id.
                }
            } else {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    // This "if - else if" block gives backwards compatibility for older scripts.
                    if (value.startsWith("@+id/")) {
                        value = value.substring(5);
                    } else if (value.startsWith("@id/")) {
                        value = value.substring(4);
                    }
                    id = tryGetId(value, isNewId);
                }
            }
            if (id == 0) {
                if (isLoadingLayout) {
                    if (isNewId) {
                        mErrors.add("failed to set view id. Make sure id is prefixed by either \"@id/\" or \"@+id/\"");
                    } else {
                        mErrors.add("failed to set view id. Make sure it is defined in the R.id class");
                    }
                } else if (!isNewId) {
                    mErrors.add("failed to find view matching id");
                }
            }
            return id;
        }

        private int tryGetId(String value, boolean isNewId) {
            Integer id;
            if (isNewId) {
                id = mNextSeq++;
                mIdList.put(value, id);
            } else {
                id = mIdList.get(value);
                if (id == null) id = 0;
            }
            return id;
        }

        public float getScaledSize(String value) {
            int i;
            float size;
            String unit = "px";
            for (i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (!(Character.isDigit(c) || c == '.')) {
                    break;
                }
            }
            try {
                size = Float.parseFloat(value.substring(0, i));
                if (i < value.length()) {
                    unit = value.substring(i).trim();
                }
                if (unit.equals("px")) {
                    return size;
                }
                if (unit.equals("sp")) {
                    return mMetrics.scaledDensity * size;
                }
                if (unit.equals("dp") || unit.equals("dip")) {
                    return mMetrics.density * size;
                }
                float inches = mMetrics.ydpi * size;
                if (unit.equals("in")) {
                    return inches;
                }
                if (unit.equals("pt")) {
                    return inches / 72;
                }
                if (unit.equals("mm")) {
                    return (float) (inches / 25.4);
                }
            } catch (NumberFormatException e) {
            }
            mErrors.add("invalid dimension value");
            return 0;
        }

        public int getColor(String value) {
            int a = 0xff, r = 0, g = 0, b = 0;
            if (value.startsWith("#") && value.length() <= 9) {
                try {
                    value = value.substring(1);
                    if (value.length() == 4) {
                        a = expandColor(value.substring(0, 1));
                        value = value.substring(1);
                    }
                    if (value.length() == 3) {
                        r = expandColor(value.substring(0, 1));
                        g = expandColor(value.substring(1, 2));
                        b = expandColor(value.substring(2, 3));
                    } else {
                        if (value.length() == 8) {
                            a = Integer.parseInt(value.substring(0, 2), 16);
                            value = value.substring(2);
                        }
                        if (value.length() == 6) {
                            r = Integer.parseInt(value.substring(0, 2), 16);
                            g = Integer.parseInt(value.substring(2, 4), 16);
                            b = Integer.parseInt(value.substring(4, 6), 16);
                        }
                    }
                    long result = (a << 24) | (r << 16) | (g << 8) | b;
                    return (int) result;
                } catch (Exception e) {
                }
            } else if (value.startsWith("?") || value.startsWith("@")) {
                int colorRes = parseTheme(value);
                if (colorRes != 0) {
                    return mContext.getResources().getColor(colorRes);
                }
            } else if (mColorNames.containsKey(value.toLowerCase())) {
                return getColor(mColorNames.get(value.toLowerCase()));
            }
            mErrors.add("Unknown color " + value);
            return 0;
        }

        /** Expand single digit color to 2 digits. */
        private int expandColor(String colorValue) {
            return Integer.parseInt(colorValue + colorValue, 16);
        }

        public int getInteger(Class<?> clazz, String attr, String value) {
            int result;
            if (value.contains("|")) {
                int work = 0;
                for (String s : value.split("\\|")) {
                    work |= getInteger(clazz, attr, s);
                }
                result = work;
            } else {
                if (value.startsWith("?") || value.startsWith("@")) {
                    result = parseTheme(value);
                } else if (value.startsWith("0x")) {
                    try {
                        result = (int) Long.parseLong(value.substring(2), 16);
                    } catch (NumberFormatException e) {
                        result = 0;
                    }
                } else if (value.matches("\\-?\\d+")) {
                    result = Integer.parseInt(value);
                } else if (value.matches("\\-?\\d+\\.\\d*|\\-?\\.\\d+")) {
                    // Strangely, some view attrs accept float numbers in XML but their related methods expect int.
                    result = (int) Float.parseFloat(value);
                } else if (clazz == InputType.class) {
                    return getInputType(value);
                } else {
                    Field f;
                    try {
                        f = clazz.getField(value.toUpperCase());
                        result = f.getInt(null);
                    } catch (Exception ex1) {
                        try {
                            f = clazz.getField(toUnderscore(value).toUpperCase());
                            result = f.getInt(null);
                        } catch (Exception ex2) {
                            try {
                                f = clazz.getField(toUnderscore(attr + '_' + value).toUpperCase());
                                result = f.getInt(null);
                            } catch (Exception ex3) {
                                mErrors.add("Unknown value: " + value);
                                result = 0;
                            }
                        }
                    }
                }
            }
            return result;
        }

/*        public int getInteger(View view, String attr, String value) {
            return getInteger(view.getClass(), attr, value);
        }*/

        public int getInteger(Class<?> clazz, String value) {
            return getInteger(clazz, "", value);
        }

        public int getInteger(View view, String value) {
            return getInteger(view.getClass(), value);
        }

        private int parseTheme(String value) {
            int result;
            try {
                String query = "";
                int i;
                value = value.substring(1); // skip past "?"
                i = value.indexOf(":");
                if (i >= 0) {
                    query = value.substring(0, i) + ".";
                    value = value.substring(i + 1);
                }
                query += "R";
                i = value.indexOf("/");
                if (i >= 0) {
                    query += "$" + value.substring(0, i);
                    value = value.substring(i + 1);
                }
                Class<?> clazz = Class.forName(query);
                Field f = clazz.getField(value);
                result = f.getInt(null);
            } catch (Exception e) {
                result = 0;
            }
            return result;
        }

        private int getInputType(String value) {
            int result = 0;
            Integer v = getInputTypes().get(value);
            if (v == null) {
                mErrors.add("Unknown input type " + value);
            } else {
                result = v;
            }
            return result;
        }

        private void setIntegerField(Object target, String fieldName, int value) {
            try {
                Field f = target.getClass().getField(fieldName);
                f.setInt(target, value);
            } catch (Exception e) {
                mErrors.add("set field)" + fieldName + " failed. " + e.toString());
            }
        }

        private void setInteger(View view, String attr, int value) {
            String name = "set" + toPascalCase(attr);
            Method m;
            try {
                if ((m = tryMethod(view, name, Context.class, int.class)) != null) {
                    m.invoke(view, mContext, value);
                } else if ((m = tryMethod(view, name, int.class)) != null) {
                    m.invoke(view, value);
                }
            } catch (Exception e) {
                addLn(name + ":" + value + ":" + e.toString());
            }
        }

        private void setFloat(View view, String attr, float value) {
            String name = "set" + toPascalCase(attr);
            Method m;
            try {
                if ((m = tryMethod(view, name, Context.class, float.class)) != null) {
                    m.invoke(view, mContext, value);
                } else if ((m = tryMethod(view, name, float.class)) != null) {
                    m.invoke(view, value);
                }
            } catch (Exception e) {
                addLn(name + ":" + value + ":" + e.toString());
            }
        }

        private Drawable getDrawable(String value) {
            try {
                Uri uri = Uri.parse(value);
                if ("file".equals(uri.getScheme())) {
                    return new BitmapDrawable(mContext.getResources(), uri.getPath());
                }
            } catch (Exception e) {
                mErrors.add("failed to load drawable " + value);
            }
            return null;
        }

        private int getLayoutValue(String value) {
            if (value == null) {
                return 0;
            }
            if (value.equals("match_parent")) {
                return LayoutParams.MATCH_PARENT;
            }
            if (value.equals("wrap_content")) {
                return LayoutParams.WRAP_CONTENT;
            }
            if (value.equals("fill_parent")) {
                return LayoutParams.MATCH_PARENT;
            }
            return (int) getScaledSize(value);
        }

        private boolean isInstanceOf(View view, int clazz) {
            boolean result;
            String c;
            switch (clazz) {
                case TEXT_VIEW:
                    result = view instanceof TextView;
                    c = "TextView";
                    break;
                default:
                    result = false;
                    c = "Class undefined";
            }
            if (!result) {
                mErrors.add("view must be instance of either " + c + " or a subclass of it");
            }
            return result;
        }
    }
}
