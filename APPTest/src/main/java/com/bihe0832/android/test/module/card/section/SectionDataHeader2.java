package com.bihe0832.android.test.module.card.section;

import com.bihe0832.android.lib.adapter.CardBaseHolder;
import com.bihe0832.android.lib.adapter.CardBaseModule;
import com.bihe0832.android.test.R;

/**
 * @author hardyshi code@bihe0832.com
 * Created on 2019-11-21.
 * Description: Description
 */
public class SectionDataHeader2 extends CardBaseModule {

    public int getResID() {
        return R.layout.card_demo_section_header_2;
    }

    public Class<? extends CardBaseHolder> getViewHolderClass() {
        return SectionHolderHeader2.class;
    }

    public String mHeaderText;

    public SectionDataHeader2(String netType) {
        super("");
        mHeaderText = netType;
    }

}