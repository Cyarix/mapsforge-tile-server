package org.develar.mapsforgeTileServer;

import org.jetbrains.annotations.NotNull;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.awt.AwtGraphicFactory;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.MapDatabase;
import org.mapsforge.map.reader.header.FileOpenResult;

import java.awt.image.BufferedImage;
import java.io.File;

public class TileRenderer {
  private final DisplayModel displayModel;

  private final DatabaseRenderer databaseRenderer;

  private final String mapFileLastModified;
  private final String renderThemeEtag;

  public TileRenderer(@NotNull DisplayModel displayModel, @NotNull File mapFile, @NotNull RenderThemeItem renderTheme, @NotNull DatabaseRenderer.TileCacheInfoProvider tileCacheInfoProvider) {
    this.displayModel = displayModel;
    MapDatabase mapDatabase = new MapDatabase();
    databaseRenderer = new DatabaseRenderer(mapDatabase, MapsforgeTileServer.GRAPHIC_FACTORY, tileCacheInfoProvider);
    databaseRenderer.setRenderTheme(renderTheme.renderTheme);

    mapFileLastModified = Long.toUnsignedString(mapFile.lastModified(), 32);
    renderThemeEtag = renderTheme.etag;

    FileOpenResult result = mapDatabase.openFile(mapFile);
    if (!result.isSuccess()) {
      throw new IllegalArgumentException(result.getErrorMessage());
    }
  }

  @NotNull
  public BoundingBox getBoundingBox() {
    return databaseRenderer.getMapDatabase().getMapFileInfo().boundingBox;
  }

  @NotNull
  public String computeETag(@NotNull TileRequest tile, @NotNull StringBuilder stringBuilder) {
    stringBuilder.setLength(0);
    stringBuilder.append(mapFileLastModified).append('@');
    stringBuilder.append(renderThemeEtag).append('@');
    stringBuilder.append(Integer.toString(tile.getImageFormat().ordinal(), 32)).append('.');
    stringBuilder.append(Integer.toString(tile.zoomLevel, 32)).append('-').append(Long.toUnsignedString(tile.tileX, 32)).append('-').append(Long.toUnsignedString(tile.tileX, 32));
    return stringBuilder.toString();
  }

  @NotNull
  public BufferedImage render(@NotNull Tile tile) {
    return AwtGraphicFactory.getBitmap(databaseRenderer.renderTile(tile, 1, false, false, displayModel));
  }
}
