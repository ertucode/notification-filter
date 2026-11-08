package dev.ertu.quietfilter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BlockSummaryDismissReceiver extends BroadcastReceiver {
    static final String EXTRA_BLOCK_COUNT = "block_count";

    @Override
    public void onReceive(Context context, Intent intent) {
        int count = intent == null ? 0 : intent.getIntExtra(EXTRA_BLOCK_COUNT, 0);
        FilterNotificationService.acknowledgeBlockSummary(context, count);
    }
}
