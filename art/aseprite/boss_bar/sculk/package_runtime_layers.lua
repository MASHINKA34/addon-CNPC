local W = 256
local H = 44

local function join(...)
  local parts = {...}
  local result = parts[1]
  for i = 2, #parts do
    result = app.fs.joinPath(result, parts[i])
  end
  return result
end

local project = app.params["projectDir"] or app.fs.currentPath
local textureRoot = join(project, "src", "main", "resources", "assets", "cnpcgeckoaddon", "textures", "gui")
local runtimeDir = join(textureRoot, "boss_bar", "sculk")
local sourceDir = join(project, "art", "aseprite", "boss_bar", "sculk")
local inputs = {
  {name = "background", path = join(textureRoot, "boss_bar_sculk_base.png")},
  {name = "fill", path = join(textureRoot, "boss_bar_sculk_fill.png")},
  {name = "frame", path = join(textureRoot, "boss_bar_sculk_overlay.png")}
}

local opened = {}
for _, item in ipairs(inputs) do
  local source = app.open(item.path)
  if not source or source.width ~= W or source.height ~= H or not source.cels[1] then
    error(item.path)
  end
  opened[#opened + 1] = source
end

local sprite = Sprite(W, H, ColorMode.RGB)

local function copyImage(source)
  local result = Image(W, H, ColorMode.RGB)
  result:clear(app.pixelColor.rgba(0, 0, 0, 0))
  for y = 0, H - 1 do
    for x = 0, W - 1 do
      result:drawPixel(x, y, source:getPixel(x, y))
    end
  end
  return result
end

for index, item in ipairs(inputs) do
  local layer = index == 1 and sprite.layers[1] or sprite:newLayer()
  layer.name = item.name
  local cel = layer:cel(1)
  if cel then
    cel.image:clear(app.pixelColor.rgba(0, 0, 0, 0))
    local sourceImage = opened[index].cels[1].image
    for y = 0, H - 1 do
      for x = 0, W - 1 do
        cel.image:drawPixel(x, y, sourceImage:getPixel(x, y))
      end
    end
  else
    sprite:newCel(layer, 1, copyImage(opened[index].cels[1].image), Point(0, 0))
  end
end

local saved = sprite:saveAs(join(sourceDir, "sculk_runtime_actual.aseprite"))
sprite:close()
for _, source in ipairs(opened) do
  source:close()
end
if not saved then error("save") end

print(runtimeDir)
