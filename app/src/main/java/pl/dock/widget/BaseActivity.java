
package pl.dock.widget;

import android.app.Activity;
import android.content.Context;

public class BaseActivity extends Activity {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Scale.wrap(base));
    }
}







