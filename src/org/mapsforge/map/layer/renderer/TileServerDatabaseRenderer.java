package org.mapsforge.map.layer.renderer;

import org.mapsforge.core.graphics.*;
import org.mapsforge.core.mapelements.MapElementContainer;
import org.mapsforge.core.mapelements.SymbolContainer;
import org.mapsforge.core.model.Point;
import org.mapsforge.core.model.Tag;
import org.mapsforge.core.model.Tile;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.MapDatabase;
import org.mapsforge.map.reader.MapReadResult;
import org.mapsforge.map.reader.PointOfInterest;
import org.mapsforge.map.reader.Way;
import org.mapsforge.map.rendertheme.RenderCallback;
import org.mapsforge.map.rendertheme.rule.RenderTheme;
import org.mapsforge.map.util.LayerUtil;

import java.util.*;

public class TileServerDatabaseRenderer implements RenderCallback {
  private static final byte LAYERS = 11;
  private static final Tag TAG_NATURAL_WATER = new Tag("natural", "water");

  private static Point[] getTilePixelCoordinates(int tileSize) {
    Point[] result = new Point[5];
    result[0] = new Point(0, 0);
    result[1] = new Point(tileSize, 0);
    result[2] = new Point(tileSize, tileSize);
    result[3] = new Point(0, tileSize);
    result[4] = result[0];
    return result;
  }

  private static byte getValidLayer(byte layer) {
    if (layer < 0) {
      return 0;
    }
    else if (layer >= LAYERS) {
      return LAYERS - 1;
    }
    else {
      return layer;
    }
  }

  private final CanvasRastererEx canvasRasterer;
  private List<MapElementContainer> currentLabels;
  private Set<MapElementContainer> currentWayLabels;
  private List<List<ShapePaintContainer>> drawingLayers;
  private GraphicFactory graphicFactory;
  private final MapDatabase mapDatabase;
  private RenderTheme renderTheme;
  private List<List<List<ShapePaintContainer>>> ways;

  public TileServerDatabaseRenderer(MapDatabase mapDatabase, GraphicFactory graphicFactory) {
    this.mapDatabase = mapDatabase;
    this.graphicFactory = graphicFactory;

    canvasRasterer = new CanvasRastererEx(graphicFactory);
  }

  public void setRenderTheme(RenderTheme renderTheme) {
    if (this.renderTheme != renderTheme) {
      this.renderTheme = renderTheme;
      ways = createWayLists();
    }
  }

  public TileBitmap renderTile(Tile tile, boolean labelsOnly, boolean hasAlpha, DisplayModel displayModel) {
    final int tileSize = tile.tileSize;

    currentLabels = new LinkedList<>();
    currentWayLabels = new HashSet<>();

    if (mapDatabase != null) {
      processReadMapData(ways, mapDatabase.readMapData(tile), tile);
    }

    TileBitmap bitmap = null;
    if (!labelsOnly) {
      bitmap = graphicFactory.createTileBitmap(tileSize, hasAlpha);
      canvasRasterer.setCanvasBitmap(bitmap);
      if (displayModel.getBackgroundColor() != renderTheme.getMapBackground()) {
        canvasRasterer.fill(hasAlpha ? 0 : renderTheme.getMapBackground());
      }
      canvasRasterer.drawWays(ways);
    }

    List<MapElementContainer> currentElementsOrdered = LayerUtil.collisionFreeOrdered(currentLabels);
    // now draw the ways and the labels
    canvasRasterer.drawMapElements(currentWayLabels, tile);
    canvasRasterer.drawMapElements(currentElementsOrdered, tile);

    // clear way list
    for (int i = ways.size() - 1; i >= 0; --i) {
      List<List<ShapePaintContainer>> innerWayList = ways.get(i);
      for (int j = innerWayList.size() - 1; j >= 0; --j) {
        innerWayList.get(j).clear();
      }
    }

    return bitmap;
  }

  @Override
  public void renderArea(PolylineContainer way, Paint fill, Paint stroke, int level) {
    List<ShapePaintContainer> list = drawingLayers.get(level);
    list.add(new ShapePaintContainer(way, stroke));
    list.add(new ShapePaintContainer(way, fill));
  }

  @Override
  public void renderAreaCaption(PolylineContainer way, int priority, String caption, float horizontalOffset, float verticalOffset,
                                Paint fill, Paint stroke, Position position, int maxTextWidth) {
    Point centerPoint = way.getCenterAbsolute().offset(horizontalOffset, verticalOffset);
    currentLabels.add(graphicFactory.createPointTextContainer(centerPoint, priority, caption, fill, stroke, null, position, maxTextWidth));
  }

  @Override
  public void renderAreaSymbol(PolylineContainer way, int priority, Bitmap symbol) {
    Point centerPosition = way.getCenterAbsolute();
    currentLabels.add(new SymbolContainer(centerPosition, priority, symbol));
  }

  @Override
  public void renderPointOfInterestCaption(PointOfInterest poi, int priority, String caption, float horizontalOffset, float verticalOffset,
                                           Paint fill, Paint stroke, Position position, int maxTextWidth, Tile tile) {
    Point poiPosition = MercatorProjection.getPixelAbsolute(poi.position, tile.zoomLevel, tile.tileSize);
    currentLabels.add(graphicFactory.createPointTextContainer(poiPosition.offset(horizontalOffset, verticalOffset), priority, caption, fill,
      stroke, null, position, maxTextWidth));
  }

  @Override
  public void renderPointOfInterestCircle(PointOfInterest poi, float radius, Paint fill, Paint stroke, int level, Tile tile) {
    List<ShapePaintContainer> list = drawingLayers.get(level);
    Point poiPosition = MercatorProjection.getPixelRelativeToTile(poi.position, tile);
    list.add(new ShapePaintContainer(new CircleContainer(poiPosition, radius), stroke));
    list.add(new ShapePaintContainer(new CircleContainer(poiPosition, radius), fill));
  }

  @Override
  public void renderPointOfInterestSymbol(PointOfInterest poi, int priority, Bitmap symbol, Tile tile) {
    Point poiPosition = MercatorProjection.getPixelAbsolute(poi.position, tile.zoomLevel, tile.tileSize);
    currentLabels.add(new SymbolContainer(poiPosition, priority, symbol));
  }

  @Override
  public void renderWay(PolylineContainer way, Paint stroke, float dy, int level) {
    drawingLayers.get(level).add(new ShapePaintContainer(way, stroke, dy));
  }

  @Override
  public void renderWaySymbol(PolylineContainer way, int priority, Bitmap symbol, float dy, boolean alignCenter, boolean repeat,
                              float repeatGap, float repeatStart, boolean rotate) {
    WayDecorator.renderSymbol(symbol, priority, dy, alignCenter, repeat, repeatGap,
      repeatStart, rotate, way.getCoordinatesAbsolute(), currentLabels);
  }

  @Override
  public void renderWayText(PolylineContainer way, int priority, String textKey, float dy, Paint fill, Paint stroke) {
    WayDecorator.renderText(textKey, priority, dy, fill, stroke, way.getCoordinatesAbsolute(), currentWayLabels);
  }

  private List<List<List<ShapePaintContainer>>> createWayLists() {
    List<List<List<ShapePaintContainer>>> result = new ArrayList<>(LAYERS);
    int levels = renderTheme.getLevels();
    for (byte i = LAYERS - 1; i >= 0; --i) {
      List<List<ShapePaintContainer>> innerWayList = new ArrayList<>(levels);
      for (int j = levels - 1; j >= 0; --j) {
        innerWayList.add(new ArrayList<>(0));
      }
      result.add(innerWayList);
    }
    return result;
  }

  private void processReadMapData(List<List<List<ShapePaintContainer>>> ways, MapReadResult mapReadResult, Tile tile) {
    if (mapReadResult == null) {
      return;
    }

    for (PointOfInterest pointOfInterest : mapReadResult.pointOfInterests) {
      renderPointOfInterest(ways, pointOfInterest, tile);
    }

    for (Way way : mapReadResult.ways) {
      renderWay(ways, new PolylineContainer(way, tile));
    }

    if (mapReadResult.isWater) {
      renderWaterBackground(ways, tile);
    }
  }

  private void renderPointOfInterest(List<List<List<ShapePaintContainer>>> ways, PointOfInterest pointOfInterest, Tile tile) {
    drawingLayers = ways.get(getValidLayer(pointOfInterest.layer));
    renderTheme.matchNode(this, pointOfInterest, tile);
  }

  private void renderWaterBackground(List<List<List<ShapePaintContainer>>> ways, Tile tile) {
    drawingLayers = ways.get(0);
    Point[] coordinates = getTilePixelCoordinates(tile.tileSize);
    PolylineContainer way = new PolylineContainer(coordinates, tile, Arrays.asList(TAG_NATURAL_WATER));
    renderTheme.matchClosedWay(this, way);
  }

  private void renderWay(List<List<List<ShapePaintContainer>>> ways, PolylineContainer way) {
    drawingLayers = ways.get(getValidLayer(way.getLayer()));

    if (way.isClosedWay()) {
      renderTheme.matchClosedWay(this, way);
    }
    else {
      renderTheme.matchLinearWay(this, way);
    }
  }
}