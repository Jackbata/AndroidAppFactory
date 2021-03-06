package com.bihe0832.android.test.module.card.section;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.bihe0832.android.lib.adapter.CardBaseHolder;
import com.bihe0832.android.lib.adapter.CardBaseModule;
import com.bihe0832.android.test.R;

/**
 * @author hardyshi code@bihe0832.com
 * Created on 2019-11-21.
 * Description: Description
 */

public class SectionHolderContent extends CardBaseHolder {
    public TextView mHeader;

    public SectionHolderContent(View itemView, Context context) {
        super(itemView, context);
    }

    @Override
    public void initView() {
        mHeader = getView(R.id.demo_text);
    }

    @Override
    public void initData(CardBaseModule item) {
        SectionDataContent data = (SectionDataContent) item;
        mHeader.setText(data.mContentText);
    }
}
