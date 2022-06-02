package com.siy.tansaga.test;

import android.util.Log;

import com.siy.tansaga.base.Invoker;
import com.siy.tansaga.base.Self;

/**
 * @author Siy
 * @since 2022/6/2
 */
public class HookJava {
    public int hookReplace(int a, int b) {
        Log.e("siy", "HookJava-hookReplace-");
        OriginJava originJava = (OriginJava) Self.get();
        originJava.showToast();

        //我们可以在这儿修改参数
        a = a + 5;
        b = b + 5;
        int orgResult = (int) Invoker.invoke();

        Log.e("siy", "orginResult:" + orgResult);
        return a - b;
    }

    public int hookProxy(int a, int b) {
        Log.e("siy", "HookJava-hookProxy-");
       // OriginJava originJava = (OriginJava) Self.get();
       // int c = originJava.proxy(1,2);

        return a - b;
    }


    /**
     * 用来代理系统的方法
     *
     * @param resId
     * @return
     */
    public String hookProxySys(int resId) {
        Log.e("siy", "HookJava-hookProxySys-");
     //   Context context = (Context) Self.get();
     //   context.getString(R.string.next);
        return   "aaaaaaaa";//context.getString(R.string.next);
    }
}
