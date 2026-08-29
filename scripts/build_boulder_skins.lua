-- Exports the six boss boulder skins from their Aseprite sources.
--
--   aseprite --batch --script-param projectDir=<repo> --script scripts/build_boulder_skins.lua
--
-- Each source is one 64x32 RGBA frame carrying the named layers of the drawing;
-- flattening it gives the runtime sheet exactly, so nothing is composed here.
-- scripts/generate_boulder_skins.py writes the same files without Aseprite.

local SHEET_WIDTH = 64
local SHEET_HEIGHT = 32
local LAYER_NAMES = {"rock", "cracks", "detail"}

-- The islands of the plain texOffs(0, 0) unwrap of a 16-cube. Everything outside
-- them has to stay clear, or the boulder picks up skirts where the cube has none.
local ISLANDS = {
  {16, 0}, {32, 0}, {0, 16}, {16, 16}, {32, 16}, {48, 16}
}

local function join(...)
  local parts = {...}
  local result = parts[1]
  for index = 2, #parts do
    result = app.fs.joinPath(result, parts[index])
  end
  return result
end

local function insideIsland(x, y)
  for _, island in ipairs(ISLANDS) do
    if x >= island[1] and x < island[1] + 16 and y >= island[2] and y < island[2] + 16 then
      return true
    end
  end
  return false
end

local function checkLayers(source)
  local found = {}
  for _, layer in ipairs(source.layers) do
    found[layer.name] = true
  end
  for _, name in ipairs(LAYER_NAMES) do
    if not found[name] then
      error(source.filename .. " is missing its '" .. name .. "' layer")
    end
  end
end

-- Cutout rendering has no half pixels, and the unwrap has no artwork off its
-- islands. Both are cheap to get wrong by hand, so both are checked on the way out.
local function checkAlpha(image)
  for y = 0, SHEET_HEIGHT - 1 do
    for x = 0, SHEET_WIDTH - 1 do
      local alpha = app.pixelColor.rgbaA(image:getPixel(x, y))
      local wanted = insideIsland(x, y) and 255 or 0
      if alpha ~= wanted then
        error(string.format("pixel %d,%d has alpha %d, expected %d", x, y, alpha, wanted))
      end
    end
  end
end

local project = app.params["projectDir"] or app.fs.currentPath
local sourceDir = join(project, "art", "aseprite", "boulder")
local entityDir = join(
  project,
  "src", "main", "resources", "assets", "cnpcgeckoaddon",
  "textures", "entity", "boulder"
)
local styleIds = {"stone", "magma", "sculk", "mossy", "bone", "ghost"}

app.fs.makeDirectory(entityDir)

for _, styleId in ipairs(styleIds) do
  local sourcePath = join(sourceDir, styleId .. ".aseprite")
  local source = app.open(sourcePath)
  if not source then error("Could not open " .. sourcePath) end
  if source.width ~= SHEET_WIDTH or source.height ~= SHEET_HEIGHT then
    source:close()
    error(sourcePath .. " must be a 64x32 source canvas")
  end
  if #source.frames ~= 1 or source.colorMode ~= ColorMode.RGB then
    source:close()
    error(sourcePath .. " must be a single RGBA frame")
  end
  checkLayers(source)

  local flattened = Image(source.spec)
  flattened:clear(app.pixelColor.rgba(0, 0, 0, 0))
  flattened:drawSprite(source, 1, Point(0, 0))
  checkAlpha(flattened)

  local output = Sprite(SHEET_WIDTH, SHEET_HEIGHT, ColorMode.RGB)
  local target = output.layers[1]:cel(1).image
  target:clear(app.pixelColor.rgba(0, 0, 0, 0))
  target:drawImage(flattened, Point(0, 0))
  local outputPath = join(entityDir, styleId .. ".png")
  local saved = output:saveAs(outputPath)
  output:close()
  source:close()
  if not saved then error("Could not save " .. outputPath) end
  print("exported " .. styleId)
end
