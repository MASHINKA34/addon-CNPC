local CHEST_SIZE = 64
local PARTICLE_SIZE = 16

local function join(...)
  local parts = {...}
  local result = parts[1]
  for index = 2, #parts do
    result = app.fs.joinPath(result, parts[index])
  end
  return result
end

local function renderFrame(source, frameNumber)
  local rendered = Image(source.spec)
  rendered:clear(app.pixelColor.rgba(0, 0, 0, 0))
  rendered:drawSprite(source, frameNumber, Point(0, 0))
  return rendered
end

local function saveRegion(sourceImage, x, y, width, height, outputPath)
  local output = Sprite(width, height, ColorMode.RGB)
  local target = output.layers[1]:cel(1).image
  target:clear(app.pixelColor.rgba(0, 0, 0, 0))
  target:drawImage(sourceImage, Point(-x, -y))
  local saved = output:saveAs(outputPath)
  output:close()
  if not saved then error("Could not save " .. outputPath) end
end

local project = app.params["projectDir"] or app.fs.currentPath
local sourceDir = join(project, "art", "aseprite", "chest")
local entityDir = join(
  project, "src", "main", "resources", "assets", "cnpcgeckoaddon",
  "textures", "entity", "chest"
)
local blockDir = join(
  project, "src", "main", "resources", "assets", "cnpcgeckoaddon",
  "textures", "block"
)
local styleIds = {"moss_cave", "infernal", "ghost", "sculk", "gilded", "bone"}

app.fs.makeDirectory(entityDir)
app.fs.makeDirectory(blockDir)

for _, styleId in ipairs(styleIds) do
  local sourcePath = join(sourceDir, styleId .. ".aseprite")
  local source = app.open(sourcePath)
  if not source then error("Could not open " .. sourcePath) end
  if source.width ~= CHEST_SIZE or source.height ~= CHEST_SIZE then
    source:close()
    error(sourcePath .. " must be a 64x64 source canvas")
  end
  if #source.frames ~= 2 or source.colorMode ~= ColorMode.RGB then
    source:close()
    error(sourcePath .. " must contain chest and particle RGBA frames")
  end

  for _, layer in ipairs(source.layers) do
    if layer.name == "vanilla_guide" then layer.isVisible = false end
  end
  local chest = renderFrame(source, 1)
  local particle = renderFrame(source, 2)
  saveRegion(chest, 0, 0, CHEST_SIZE, CHEST_SIZE, join(entityDir, styleId .. ".png"))
  saveRegion(
    particle, 0, 0, PARTICLE_SIZE, PARTICLE_SIZE,
    join(blockDir, "boss_chest_" .. styleId .. ".png")
  )
  source:close()
  print("exported " .. styleId)
end
