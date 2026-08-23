local WIDTH = 256
local HEIGHT = 44
local INNER_X1 = 29
local INNER_X2 = 226
local INNER_Y1 = 14
local INNER_Y2 = 31
local PREVIEW_PROGRESS = 0.70

local function join_path(...)
  local parts = {...}
  local result = parts[1]
  for index = 2, #parts do
    result = app.fs.joinPath(result, parts[index])
  end
  return result
end

local project_dir = app.params["projectDir"] or app.fs.currentPath
local script_dir = join_path(project_dir, "art", "aseprite")
local reference_path = join_path(script_dir, "references", "boss_bar_sculk_reference.png")
local aseprite_path = join_path(script_dir, "boss_bar_sculk.aseprite")
local texture_dir = join_path(
  project_dir, "src", "main", "resources", "assets", "cnpcgeckoaddon", "textures", "gui"
)
local preview_path = join_path(texture_dir, "boss_bar_sculk.png")
local base_path = join_path(texture_dir, "boss_bar_sculk_base.png")
local fill_path = join_path(texture_dir, "boss_bar_sculk_fill.png")
local overlay_path = join_path(texture_dir, "boss_bar_sculk_overlay.png")

app.fs.makeDirectory(texture_dir)

local TRANSPARENT = app.pixelColor.rgba(0, 0, 0, 0)

-- The palette follows the attached sprite: cold deepslate, dark sculk,
-- restrained teal energy, and a few hot cyan pixels.
local PALETTE = {
  {  2,   5,   9},
  {  4,   9,  15},
  {  5,  15,  23},
  {  7,  22,  32},
  { 10,  29,  41},
  { 15,  39,  51},
  { 22,  49,  61},
  { 34,  62,  73},
  { 49,  77,  87},
  { 67,  91,  99},
  {  2,  17,  25},
  {  3,  29,  40},
  {  4,  43,  57},
  {  4,  57,  73},
  {  4,  74,  92},
  {  4,  91, 111},
  {  5, 110, 130},
  {  5, 132, 148},
  {  7, 157, 169},
  { 10, 187, 194},
  { 18, 218, 219},
  { 55, 239, 231},
  {132, 255, 246},
}

for _, color in ipairs(PALETTE) do
  color.pixel = app.pixelColor.rgba(color[1], color[2], color[3], 255)
end

local EMPTY_FALLBACK = PALETTE[2].pixel
local HEALTH_FALLBACK = PALETTE[15].pixel
local VEIN_DARK = PALETTE[12].pixel
local VEIN_LIGHT = PALETTE[14].pixel

local reference = app.open(reference_path)
if not reference then
  error("Cannot open visual reference: " .. reference_path)
end
local reference_cel = reference.cels[1]
if not reference_cel then
  error("The visual reference contains no image cel")
end
local reference_image = reference_cel.image

-- Foreground bounding box measured from the attached reference. It maps almost
-- exactly to 5 source pixels per one final GUI pixel.
local CROP_X = 28
local CROP_Y = 476
local CROP_W = 1281
local CROP_H = 218

local function rgba_channels(pixel)
  return app.pixelColor.rgbaR(pixel),
         app.pixelColor.rgbaG(pixel),
         app.pixelColor.rgbaB(pixel),
         app.pixelColor.rgbaA(pixel)
end

local function is_foreground(r, g, b)
  local average = (r + g + b) / 3
  local cyan_bias = math.min(g, b) - r
  return average < 112 or (cyan_bias > 20 and average < 170)
end

local function nearest_palette(r, g, b)
  local best = PALETTE[1]
  local best_distance = math.huge
  for _, candidate in ipairs(PALETTE) do
    local dr = r - candidate[1]
    local dg = g - candidate[2]
    local db = b - candidate[3]
    local distance = dr * dr * 0.8 + dg * dg + db * db * 1.15
    if distance < best_distance then
      best_distance = distance
      best = candidate
    end
  end
  return best.pixel
end

local function sample_reference(target_x, target_y)
  local sx1 = math.floor(CROP_X + target_x * CROP_W / WIDTH)
  local sy1 = math.floor(CROP_Y + target_y * CROP_H / HEIGHT)
  local sx2 = math.max(sx1, math.floor(CROP_X + (target_x + 1) * CROP_W / WIDTH) - 1)
  local sy2 = math.max(sy1, math.floor(CROP_Y + (target_y + 1) * CROP_H / HEIGHT) - 1)

  local red, green, blue, count = 0, 0, 0, 0
  local total = (sx2 - sx1 + 1) * (sy2 - sy1 + 1)
  for sy = sy1, sy2 do
    for sx = sx1, sx2 do
      local r, g, b = rgba_channels(reference_image:getPixel(sx, sy))
      if is_foreground(r, g, b) then
        red = red + r
        green = green + g
        blue = blue + b
        count = count + 1
      end
    end
  end

  if count < math.max(2, math.floor(total * 0.16)) then
    return TRANSPARENT
  end
  return nearest_palette(red / count, green / count, blue / count)
end

local sampled = {}
for y = 0, HEIGHT - 1 do
  sampled[y] = {}
  for x = 0, WIDTH - 1 do
    sampled[y][x] = sample_reference(x, y)
  end
end

local function is_transparent(pixel)
  return app.pixelColor.rgbaA(pixel) == 0
end

local function is_glow(pixel)
  if is_transparent(pixel) then return false end
  local r, g, b = rgba_channels(pixel)
  return g >= 120 and b >= 120 and math.min(g, b) - r >= 55
end

local function inside_channel(x, y)
  return x >= INNER_X1 and x <= INNER_X2 and y >= INNER_Y1 and y <= INNER_Y2
end

local function image()
  local result = Image(WIDTH, HEIGHT, ColorMode.RGB)
  result:clear(TRANSPARENT)
  return result
end

local function px(target, x, y, color)
  if x >= 0 and x < WIDTH and y >= 0 and y < HEIGHT then
    target:drawPixel(x, y, color)
  end
end

local function rect(target, x1, y1, x2, y2, color)
  for y = y1, y2 do
    for x = x1, x2 do
      px(target, x, y, color)
    end
  end
end

local function points(target, list, color)
  for _, point in ipairs(list) do
    px(target, point[1], point[2], color)
  end
end

local frame_image = image()
local health_image = image()
local empty_image = image()
local veins_image = image()
local glow_image = image()
local runtime_fill_image = image()
local always_glow_image = image()

local EMPTY_SAMPLE_X1 = 168
local EMPTY_SAMPLE_X2 = 225
local HEALTH_SAMPLE_X1 = 31
local HEALTH_SAMPLE_X2 = 166
local preview_end = INNER_X1 + math.floor((INNER_X2 - INNER_X1 + 1) * PREVIEW_PROGRESS + 0.5) - 1

-- Build a full empty channel by repeating the reference's empty section. This
-- means decreasing health always reveals genuine dark-blue sculk texture.
for y = INNER_Y1, INNER_Y2 do
  for x = INNER_X1, INNER_X2 do
    local source_x = EMPTY_SAMPLE_X1 + ((x - INNER_X1) % (EMPTY_SAMPLE_X2 - EMPTY_SAMPLE_X1 + 1))
    local color = sampled[y][source_x]
    if is_transparent(color) or is_glow(color) then color = EMPTY_FALLBACK end
    px(empty_image, x, y, color)
  end
end

-- The editable source shows the same ~70% health state as the attached image.
-- The separate runtime fill texture extends this exact material to 100%.
for y = INNER_Y1, INNER_Y2 do
  for x = INNER_X1, INNER_X2 do
    local source_x = HEALTH_SAMPLE_X1 + ((x - INNER_X1) % (HEALTH_SAMPLE_X2 - HEALTH_SAMPLE_X1 + 1))
    local color = sampled[y][source_x]
    if is_transparent(color) then color = HEALTH_FALLBACK end
    px(runtime_fill_image, x, y, color)
    if x <= preview_end then
      if is_glow(color) then
        px(glow_image, x, y, color)
      else
        px(health_image, x, y, color)
      end
    end
  end
end

-- The silhouette, rails, sensor housings, and tendrils are a quantized direct
-- reconstruction of the user's exact reference rather than a redesign.
for y = 0, HEIGHT - 1 do
  for x = 0, WIDTH - 1 do
    if not inside_channel(x, y) then
      local color = sampled[y][x]
      if not is_transparent(color) then
        if is_glow(color) then
          px(glow_image, x, y, color)
          px(always_glow_image, x, y, color)
        else
          px(frame_image, x, y, color)
        end
      end
    end
  end
end

-- A few explicit veins keep the texture organic when the health mask moves.
points(veins_image, {
  {43, 19}, {44, 19}, {45, 20}, {46, 20}, {47, 21}, {48, 21},
  {62, 28}, {63, 27}, {64, 27}, {65, 26},
  {121, 16}, {122, 17}, {123, 17},
  {177, 18}, {178, 19}, {179, 19}, {180, 20}, {181, 20},
  {186, 27}, {187, 26}, {188, 26}, {189, 25}, {190, 25},
  {198, 16}, {199, 17}, {200, 17}, {201, 18},
  {211, 28}, {212, 27}, {213, 27}, {214, 26}
}, VEIN_DARK)
points(veins_image, {
  {48, 22}, {65, 25}, {124, 18}, {181, 21}, {190, 24}, {202, 19}, {215, 25}
}, VEIN_LIGHT)

local sprite = Sprite(WIDTH, HEIGHT, ColorMode.RGB)

local function set_layer(layer, name, source)
  layer.name = name
  local cel = layer:cel(1)
  if not cel then
    cel = sprite:newCel(layer, 1, Image(WIDTH, HEIGHT, ColorMode.RGB), Point(0, 0))
  end
  cel.image:clear(TRANSPARENT)
  for y = 0, HEIGHT - 1 do
    for x = 0, WIDTH - 1 do
      cel.image:drawPixel(x, y, source:getPixel(x, y))
    end
  end
end

set_layer(sprite.layers[1], "empty_bar", empty_image)
set_layer(sprite:newLayer(), "health_fill", health_image)
set_layer(sprite:newLayer(), "sculk_veins", veins_image)
set_layer(sprite:newLayer(), "frame", frame_image)
set_layer(sprite:newLayer(), "glow_pixels", glow_image)

local palette = Palette(#PALETTE + 1)
palette:setColor(0, Color{ r=0, g=0, b=0, a=0 })
for index, color in ipairs(PALETTE) do
  palette:setColor(index, Color{ r=color[1], g=color[2], b=color[3], a=255 })
end
sprite:setPalette(palette)

local function composite(...)
  local result = image()
  local sources = {...}
  for _, source in ipairs(sources) do
    for y = 0, HEIGHT - 1 do
      for x = 0, WIDTH - 1 do
        local color = source:getPixel(x, y)
        if not is_transparent(color) then
          result:drawPixel(x, y, color)
        end
      end
    end
  end
  return result
end

local function save_png(source, path)
  local output = Sprite(WIDTH, HEIGHT, ColorMode.RGB)
  local output_image = output.layers[1]:cel(1).image
  output_image:clear(TRANSPARENT)
  for y = 0, HEIGHT - 1 do
    for x = 0, WIDTH - 1 do
      output_image:drawPixel(x, y, source:getPixel(x, y))
    end
  end
  local saved = output:saveAs(path)
  output:close()
  if not saved then error("Could not save PNG: " .. path) end
end

-- Base is always visible. Fill is clipped horizontally by the in-game health
-- renderer, then the ornamental overlay restores veins, rails, sensors, and glows.
-- At 70% health this layering reproduces the editable master pixel-for-pixel.
local runtime_base = composite(empty_image)
local runtime_overlay = composite(veins_image, frame_image, always_glow_image)
local master_preview = composite(empty_image, health_image, veins_image, frame_image, glow_image)

save_png(runtime_base, base_path)
save_png(runtime_fill_image, fill_path)
save_png(runtime_overlay, overlay_path)
save_png(master_preview, preview_path)

local source_saved = sprite:saveAs(aseprite_path)
sprite:close()
reference:close()

if not source_saved then error("Could not save Aseprite source: " .. aseprite_path) end
print("ASEPRITE_SOURCE=" .. aseprite_path)
print("PREVIEW=" .. preview_path)
print("RUNTIME_BASE=" .. base_path)
print("RUNTIME_FILL=" .. fill_path)
print("RUNTIME_OVERLAY=" .. overlay_path)
print("LAYERS=empty_bar,health_fill,sculk_veins,frame,glow_pixels")
print("SIZE=" .. WIDTH .. "x" .. HEIGHT .. " PREVIEW_PROGRESS=" .. PREVIEW_PROGRESS)
