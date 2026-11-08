package dev.ertu.quietfilter;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class ScreenDimmerTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        int mode = ScreenDimmerService.getMode(this);
        if (mode != ScreenDimmerService.MODE_NONE && !Settings.canDrawOverlays(this)) {
            ScreenDimmerService.applyMode(this, ScreenDimmerService.MODE_NONE);
            mode = ScreenDimmerService.MODE_NONE;
        }
        updateTile(mode);
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!Settings.canDrawOverlays(this)) {
            ScreenDimmerService.applyMode(this, ScreenDimmerService.MODE_NONE);
            openOverlayPermission();
            updateTile(ScreenDimmerService.MODE_NONE);
            return;
        }
        int current = ScreenDimmerService.getMode(this);
        int next = current == ScreenDimmerService.MODE_NONE
                ? ScreenDimmerService.MODE_DARK
                : current == ScreenDimmerService.MODE_DARK
                ? ScreenDimmerService.MODE_DARKER : ScreenDimmerService.MODE_NONE;
        ScreenDimmerService.applyMode(this, next);
        updateTile(next);
    }

    private void updateTile(int mode) {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(mode == ScreenDimmerService.MODE_NONE
                ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        tile.setLabel("Screen dimmer");
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(ScreenDimmerService.modeName(mode));
        tile.setContentDescription("Screen dimmer: " + ScreenDimmerService.modeName(mode));
        tile.updateTile();
    }

    private void openOverlayPermission() {
        Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:" + getPackageName()));
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pending = PendingIntent.getActivity(this, 0, permission,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pending);
        } else {
            startActivityAndCollapse(permission);
        }
    }
}
