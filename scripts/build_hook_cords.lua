local FRAME_SIZE = 16
local FRAME_COUNT = 4

local function join(...)
  local parts = {...}
  local result = parts[1]
  for index = 2, #parts do
    result = app.fs.joinPath(result, parts[index])
  end
  return result
end

local function copyRegion(source, target, sourceX, targetY)
  for y = 0, FRAME_SIZE - 1 do
    for x = 0, FRAME_SIZE - 1 do
      target:drawPixel(x, targetY + y, source:getPixel(sourceX + x, y))
    end
  end
end

local function saveFilmstrip(source, regionX, outputPath)
  local output = Sprite(FRAME_SIZE, FRAME_SIZE * FRAME_COUNT, ColorMode.RGB)
  local target = output.layers[1]:cel(1).image
  target:clear(app.pixelColor.rgba(0, 0, 0, 0))

  for frameNumber = 1, FRAME_COUNT do
    local rendered = Image(source.spec)
    rendered:clear(app.pixelColor.rgba(0, 0, 0, 0))
    rendered:drawSprite(source, frameNumber, Point(0, 0))
    copyRegion(rendered, target, regionX, (frameNumber - 1) * FRAME_SIZE)
  end

  local saved = output:saveAs(outputPath)
  output:close()
  if not saved then error("Could not save " .. outputPath) end
end

local project = app.params["projectDir"] or app.fs.currentPath
local sourceDir = join(project, "art", "aseprite", "hook")
local outputRoot = join(
  project,
  "src", "main", "resources", "assets", "cnpcgeckoaddon",
  "textures", "entity", "hook"
)
local styleIds = {"vine", "chain_infernal", "tentacle", "ghost"}

for _, styleId in ipairs(styleIds) do
  local sourcePath = join(sourceDir, styleId .. ".aseprite")
  local outputDir = join(outputRoot, styleId)
  local source = app.open(sourcePath)
  if not source then error("Could not open " .. sourcePath) end
  if source.width ~= FRAME_SIZE * 2 or source.height ~= FRAME_SIZE then
    source:close()
    error(sourcePath .. " must be a 32x16 source canvas")
  end
  if #source.frames ~= FRAME_COUNT or source.colorMode ~= ColorMode.RGB then
    source:close()
    error(sourcePath .. " must contain four RGBA frames")
  end

  app.fs.makeDirectory(outputDir)
  saveFilmstrip(source, 0, join(outputDir, "cord.png"))
  saveFilmstrip(source, FRAME_SIZE, join(outputDir, "head.png"))
  source:close()
  print("exported " .. styleId)
end

