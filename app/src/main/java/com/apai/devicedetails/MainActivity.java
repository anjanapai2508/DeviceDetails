package com.apai.devicedetails;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    public Button showDetails;

    static final String[] FRUITS = new String[] { "Apple", "Avocado", "Banana",
            "Blueberry", "Coconut", "Durian"};

    static final String[] animalName={"Lion","Tiger","Monkey","Dog","Cat","Elephant"};
    static final ArrayList<String>displayStringArrayList = new ArrayList<String>();
    public String displayString = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView listView = (ListView)findViewById(R.id.detailsList);
         ArrayList<HashMap<String,String>> arrayList=new ArrayList<>();
        for (int i=0;i<animalName.length;i++)
        {
            displayStringArrayList.add(animalName[i] + " : " + FRUITS[i]);
        }
        ArrayAdapter adapter = new ArrayAdapter<String>(this,
                R.layout.activity_listview, displayStringArrayList);
       listView.setAdapter(adapter);

    }


}
