local W = 182
local H = 16

local function join(...)
  local parts = {...}
  local result = parts[1]
  for i = 2, #parts do
    result = app.fs.joinPath(result, parts[i])
  end
  return result
end

local project = app.params["projectDir"] or app.fs.currentPath
local sourceDir = join(project, "art", "aseprite", "boss_bar", "sculk")
local outputDir = join(project, "src", "main", "resources", "assets", "cnpcgeckoaddon", "textures", "gui", "boss_bar", "sculk")
local sourcePath = join(sourceDir, "sculk_runtime_182x16.aseprite")
local backgroundPath = join(outputDir, "background.png")
local fillPath = join(outputDir, "fill.png")
local framePath = join(outputDir, "frame.png")

local C = {
  clear = app.pixelColor.rgba(0, 0, 0, 0),
  outline = app.pixelColor.rgba(2, 5, 9, 255),
  empty = app.pixelColor.rgba(4, 9, 16, 255),
  emptyBlue = app.pixelColor.rgba(5, 18, 30, 255),
  vein = app.pixelColor.rgba(7, 31, 48, 255),
  slateDark = app.pixelColor.rgba(13, 20, 28, 255),
  slate = app.pixelColor.rgba(24, 35, 44, 255),
  slateLight = app.pixelColor.rgba(39, 54, 64, 255),
  sculkDark = app.pixelColor.rgba(3, 22, 29, 255),
  health = app.pixelColor.rgba(5, 58, 74, 255),
  healthLight = app.pixelColor.rgba(6, 79, 96, 255),
  turquoise = app.pixelColor.rgba(8, 133, 145, 255),
  cyan = app.pixelColor.rgba(20, 211, 222, 255),
  cyanHot = app.pixelColor.rgba(118, 246, 238, 255)
}

local function makeImage()
  local result = Image(W, H, ColorMode.RGB)
  result:clear(C.clear)
  return result
end

local function px(image, x, y, color)
  if x >= 0 and x < W and y >= 0 and y < H then
    image:drawPixel(x, y, color)
  end
end

local function rect(image, x1, y1, x2, y2, color)
  for y = y1, y2 do
    for x = x1, x2 do
      px(image, x, y, color)
    end
  end
end

local function points(image, list, color)
  for _, point in ipairs(list) do
    px(image, point[1], point[2], color)
  end
end

local function mirrored(image, list, color)
  points(image, list, color)
  for _, point in ipairs(list) do
    px(image, W - 1 - point[1], point[2], color)
  end
end

local background = makeImage()
local fill = makeImage()
local frame = makeImage()

rect(background, 11, 4, 170, 12, C.empty)
rect(background, 11, 4, 170, 4, C.emptyBlue)
rect(background, 11, 12, 170, 12, C.sculkDark)
points(background, {
  {16, 7}, {17, 7}, {18, 8}, {19, 8}, {20, 9}, {21, 9},
  {25, 5}, {26, 6}, {27, 6}, {28, 7},
  {33, 11}, {34, 10}, {35, 10}, {36, 9}, {37, 9},
  {144, 6}, {145, 6}, {146, 7}, {147, 7}, {148, 8},
  {153, 11}, {154, 10}, {155, 10}, {156, 9},
  {162, 5}, {163, 6}, {164, 6}, {165, 7}
}, C.vein)

rect(fill, 11, 4, 170, 12, C.health)
rect(fill, 11, 4, 170, 4, C.healthLight)
rect(fill, 11, 12, 170, 12, C.sculkDark)
rect(fill, 14, 5, 17, 11, C.healthLight)
rect(fill, 164, 5, 167, 11, C.healthLight)
points(fill, {
  {19, 7}, {20, 7}, {21, 8}, {22, 8},
  {29, 10}, {30, 9}, {31, 9}, {32, 8},
  {149, 6}, {150, 7}, {151, 7}, {152, 8},
  {159, 10}, {160, 9}, {161, 9}
}, C.turquoise)

rect(frame, 10, 2, 171, 2, C.outline)
rect(frame, 10, 14, 171, 14, C.outline)
rect(frame, 11, 3, 170, 3, C.slate)
rect(frame, 11, 13, 170, 13, C.slateDark)
for x = 14, 168, 10 do
  px(frame, x, 3, C.slateLight)
end
for x = 18, 168, 12 do
  px(frame, x, 13, C.slate)
end

local sensorRows = {
  {2, 5, 10}, {3, 3, 12}, {4, 2, 14}, {5, 1, 15},
  {6, 1, 15}, {7, 0, 15}, {8, 0, 15}, {9, 0, 15},
  {10, 1, 15}, {11, 1, 14}, {12, 2, 13}, {13, 4, 11}
}
for _, row in ipairs(sensorRows) do
  local y = row[1]
  local x1 = row[2]
  local x2 = row[3]
  rect(frame, x1, y, x2, y, C.outline)
  rect(frame, W - 1 - x2, y, W - 1 - x1, y, C.outline)
end

local sensorInnerRows = {
  {3, 5, 10}, {4, 4, 12}, {5, 3, 13}, {6, 2, 13},
  {7, 2, 14}, {8, 2, 14}, {9, 2, 14}, {10, 3, 13},
  {11, 3, 12}, {12, 5, 10}
}
for _, row in ipairs(sensorInnerRows) do
  local y = row[1]
  local x1 = row[2]
  local x2 = row[3]
  rect(frame, x1, y, x2, y, C.slateDark)
  rect(frame, W - 1 - x2, y, W - 1 - x1, y, C.slateDark)
end

mirrored(frame, {
  {5, 4}, {7, 3}, {10, 4}, {3, 6}, {12, 5}, {2, 9},
  {4, 11}, {6, 12}, {9, 12}, {12, 10}, {13, 7}
}, C.slate)
mirrored(frame, {
  {5, 6}, {6, 5}, {9, 5}, {11, 6}, {4, 8}, {11, 10},
  {6, 11}, {9, 10}
}, C.sculkDark)
mirrored(frame, {
  {6, 6}, {7, 5}, {8, 5}, {9, 6}, {10, 6},
  {5, 7}, {11, 7}, {5, 8}, {11, 8}, {5, 9}, {10, 9},
  {6, 10}, {7, 11}, {8, 11}, {9, 10}
}, C.outline)
mirrored(frame, {
  {6, 7}, {7, 6}, {8, 6}, {9, 7}, {10, 7},
  {6, 8}, {7, 8}, {8, 8}, {9, 8}, {10, 8},
  {6, 9}, {7, 9}, {8, 9}, {9, 9}
}, C.turquoise)
mirrored(frame, {
  {7, 7}, {8, 7}, {7, 8}, {8, 8}
}, C.cyan)
mirrored(frame, {{7, 7}}, C.cyanHot)

mirrored(frame, {
  {5, 2}, {4, 1}, {3, 1}, {2, 0},
  {2, 5}, {1, 4}, {0, 4},
  {1, 11}, {0, 12},
  {5, 13}, {4, 14}, {3, 15},
  {9, 2}, {10, 1}, {11, 0},
  {9, 13}, {10, 14}, {11, 15}
}, C.outline)
mirrored(frame, {
  {4, 1}, {1, 4}, {4, 14}, {10, 1}, {10, 14}
}, C.sculkDark)
mirrored(frame, {
  {30, 2}, {29, 1}, {29, 0}, {28, 0},
  {48, 2}, {48, 1}, {49, 1},
  {31, 14}, {32, 15}, {33, 15}
}, C.outline)
mirrored(frame, {
  {29, 1}, {48, 1}, {32, 15}
}, C.sculkDark)
mirrored(frame, {
  {28, 0}, {33, 15}
}, C.turquoise)

local sprite = Sprite(W, H, ColorMode.RGB)

local function setLayer(layer, name, source)
  layer.name = name
  local cel = layer:cel(1)
  if not cel then
    cel = sprite:newCel(layer, 1, Image(W, H, ColorMode.RGB), Point(0, 0))
  end
  cel.image:clear(C.clear)
  for y = 0, H - 1 do
    for x = 0, W - 1 do
      cel.image:drawPixel(x, y, source:getPixel(x, y))
    end
  end
end

setLayer(sprite.layers[1], "background", background)
setLayer(sprite:newLayer(), "fill", fill)
setLayer(sprite:newLayer(), "frame", frame)

local colors = {
  Color{r=0,g=0,b=0,a=0},
  Color{r=2,g=5,b=9,a=255},
  Color{r=4,g=9,b=16,a=255},
  Color{r=5,g=18,b=30,a=255},
  Color{r=7,g=31,b=48,a=255},
  Color{r=13,g=20,b=28,a=255},
  Color{r=24,g=35,b=44,a=255},
  Color{r=39,g=54,b=64,a=255},
  Color{r=3,g=22,b=29,a=255},
  Color{r=5,g=58,b=74,a=255},
  Color{r=6,g=79,b=96,a=255},
  Color{r=8,g=133,b=145,a=255},
  Color{r=20,g=211,b=222,a=255},
  Color{r=118,g=246,b=238,a=255}
}
local palette = Palette(#colors)
for index, color in ipairs(colors) do
  palette:setColor(index - 1, color)
end
sprite:setPalette(palette)

local function savePng(source, path)
  local output = Sprite(W, H, ColorMode.RGB)
  local target = output.layers[1]:cel(1).image
  target:clear(C.clear)
  for y = 0, H - 1 do
    for x = 0, W - 1 do
      target:drawPixel(x, y, source:getPixel(x, y))
    end
  end
  local saved = output:saveAs(path)
  output:close()
  if not saved then error(path) end
end

savePng(background, backgroundPath)
savePng(fill, fillPath)
savePng(frame, framePath)

local saved = sprite:saveAs(sourcePath)
sprite:close()
if not saved then error(sourcePath) end

print(sourcePath)
print(backgroundPath)
print(fillPath)
print(framePath)
