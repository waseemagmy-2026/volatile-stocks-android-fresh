<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true"
    android:background="#000000">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <TextView
            android:id="@+id/symbolText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="SYMBOL"
            android:textSize="28sp"
            android:textStyle="bold"
            android:textColor="#FFFFFF"
            android:gravity="center"
            android:paddingBottom="16dp" />

        <TextView
            android:id="@+id/chartStatusText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="טוען..."
            android:textColor="#CCCCCC"
            android:textSize="16sp"
            android:gravity="center"
            android:paddingBottom="12dp" />

        <com.github.mikephil.charting.charts.LineChart
            android:id="@+id/lineChart"
            android:layout_width="match_parent"
            android:layout_height="260dp"
            android:layout_marginBottom="16dp" />

        <TextView
            android:id="@+id/rsiText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="RSI: --"
            android:textColor="#FFFFFF"
            android:textSize="22sp"
            android:textStyle="bold"
            android:paddingBottom="12dp" />

        <TextView
            android:id="@+id/highText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="שיא יומי: --"
            android:textColor="#FFFFFF"
            android:textSize="20sp"
            android:gravity="end"
            android:paddingBottom="8dp" />

        <TextView
            android:id="@+id/lastPriceText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="מחיר אחרון: --"
            android:textColor="#FFFFFF"
            android:textSize="20sp"
            android:gravity="end"
            android:paddingBottom="20dp" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="התראת ירידה חדה (%)"
            android:textColor="#FFFFFF"
            android:textSize="18sp"
            android:gravity="end" />

        <EditText
            android:id="@+id/dropThresholdInput"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="numberDecimal"
            android:text="3"
            android:textColor="#FFFFFF"
            android:textColorHint="#AAAAAA"
            android:backgroundTint="#2196F3"
            android:gravity="end"
            android:paddingBottom="16dp" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="התראה קרוב לשיא (%)"
            android:textColor="#FFFFFF"
            android:textSize="18sp"
            android:gravity="end" />

        <EditText
            android:id="@+id/highThresholdInput"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="numberDecimal"
            android:text="0.5"
            android:textColor="#FFFFFF"
            android:textColorHint="#AAAAAA"
            android:backgroundTint="#2196F3"
            android:gravity="end"
            android:paddingBottom="20dp" />

        <Button
            android:id="@+id/saveAlertsButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="שמור התראות"
            android:layout_marginBottom="12dp" />

        <Button
            android:id="@+id/refreshButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="רענן עכשיו" />

    </LinearLayout>
</ScrollView>
