
package pl.dock.widget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Lista wszystkich aplikacji (przycisk siatki)
public class AppsActivity extends BaseActivity {

    private static class Item {
        String label;
        String pkg;
    }

    private final Map<String, Bitmap> cache = new HashMap<String, Bitmap>();

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle b) {
        boolean night = Look.night(this);
        setTheme(night ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(b);
        final Look.Pal p = Look.pal(night);

        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        final List<Item> items = new ArrayList<Item>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            Item it = new Item();
            it.label = ri.loadLabel(pm).toString();
            it.pkg = ri.activityInfo.packageName;
            items.add(it);
        }
        Collections.sort(items, new Comparator<Item>() {
            @Override
            public int compare(Item x, Item y) {
                return x.label.compareToIgnoreCase(y.label);
            }
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);
        root.setPadding(dp(24), dp(16), dp(24), 0);

        TextView title = new TextView(this);
        title.setText("Aplikacje");
        title.setTextSize(30);
        title.setTextColor(p.text);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(8), 0, 0, dp(8));
        root.addView(title);

        GridView gv = new GridView(this);
        gv.setNumColumns(GridView.AUTO_FIT);
        gv.setColumnWidth(dp(120));
        gv.setVerticalSpacing(dp(14));
        gv.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        gv.setSelector(new ColorDrawable(Color.TRANSPARENT));
        gv.setClipToPadding(false);
        gv.setPadding(0, dp(8), 0, dp(24));
        root.addView(gv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        gv.setAdapter(new ArrayAdapter<Item>(this, 0, items) {
            @Override
            public View getView(int pos, View cv, ViewGroup parent) {
                Context c = getContext();
                Item it = getItem(pos);
                LinearLayout cell = new LinearLayout(c);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                ImageView iv = new ImageView(c);
                Bitmap bm = cache.get(it.pkg);
                if (bm == null) {
                    bm = Icons.forPackage(c, it.pkg);
                    cache.put(it.pkg, bm);
                }
                iv.setImageBitmap(bm);
                cell.addView(iv, new LinearLayout.LayoutParams(dp(64), dp(64)));
                TextView t = new TextView(c);
                t.setText(it.label);
                t.setTextSize(14);
                t.setTextColor(p.text);
                t.setGravity(Gravity.CENTER);
                t.setMaxLines(2);
                t.setPadding(dp(4), dp(6), dp(4), 0);
                cell.addView(t);
                return cell;
            }
        });
        gv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
                Intent l = getPackageManager().getLaunchIntentForPackage(items.get(pos).pkg);
                if (l != null) {
                    l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(l);
                    finish();
                }
            }
        });
        setContentView(root);
    }
}







