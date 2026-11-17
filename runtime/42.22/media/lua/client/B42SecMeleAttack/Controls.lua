require "keyBinding"
require "OptionScreens/MainOptions"

local Controls = { binding = "B42SMA_FloorAttack", enabled = false }
local configFile = "B42SecMeleAttack.ini"
local legacyKey = 0

local function readLines(name, visit)
    local reader = getFileReader(name, false)
    if not reader then return end
    while true do
        local line = reader:readLine()
        if line == nil then break end
        visit(line)
    end
    reader:close()
end

local hasOwnSetting = false
readLines(configFile, function(line)
    local value = line:match("^BlockPrimaryFloor=(%a+)$")
    if value == "true" or value == "false" then
        Controls.enabled = value == "true"
        hasOwnSetting = true
    end
end)
-- Read the old values without changing the shared ModOptions file.
readLines("ModOptions.ini", function(line)
    local value = line:match("^tickbox|B42SecMeleAttack|BlockPrimaryFloor|(%a+)$")
    if not hasOwnSetting and (value == "true" or value == "false") then Controls.enabled = value == "true" end
    local key = tonumber(line:match("^keybind|B42SecMeleAttack|FloorAttack|(%d+)$"))
    if key and key >= 0 and key <= 10007 then legacyKey = key end
end)

function Controls.save(value)
    value = value == true and Controls.usableKey(Controls.key())
    local writer = getFileWriter(configFile, true, false)
    if not writer then
        print("[B42SecMeleAttack] Cannot save local control option")
        return false
    end
    writer:write("version=1\nBlockPrimaryFloor=" .. tostring(value == true) .. "\n")
    writer:close()
    Controls.enabled = value == true
    Controls.enforceAutoProne()
    return true
end

local function register()
    for i = #keyBinding, 1, -1 do
        if keyBinding[i].value == Controls.binding then table.remove(keyBinding, i) end
    end
    local position = #keyBinding + 1
    local inCombat = false
    for i, entry in ipairs(keyBinding) do
        if entry.value == "[Combat]" then inCombat = true; position = i + 1
        elseif inCombat and entry.value == KeybindId.CROUCH:getId() then position = i + 1; break
        elseif inCombat and entry.value:sub(1, 1) == "[" then break end
    end
    table.insert(keyBinding, position, {value = Controls.binding, key = 0})
end
register()

local loaded = false
local originalLoad = MainOptions.loadKeys
MainOptions.loadKeys = function(...)
    register()
    local result = originalLoad(...)
    local persisted = false
    readLines("keysB42.ini", function(line)
        if line:match("^" .. Controls.binding .. "=") then persisted = true end
    end)
    if not persisted and legacyKey > 0 then
        getCore():addKeyBinding(Controls.binding, legacyKey, 0, false, false, false)
        for _, entry in ipairs(MainOptions.keys) do
            if entry.value == Controls.binding then entry.key = legacyKey end
        end
    end
    loaded = true
    return result
end

function Controls.ensureKeys()
    if not loaded then MainOptions.loadKeys() end
    Controls.enforceAutoProne()
end

function Controls.key()
    return getCore():getKey(Controls.binding)
end

function Controls.usableKey(key)
    return type(key) == "number" and ((key > 0 and key < 10000) or (key >= 10002 and key <= 10007))
end

function Controls.blockEnabled()
    return Controls.enabled and Controls.usableKey(Controls.key())
end

function Controls.enforceAutoProne()
    local core = getCore()
    if Controls.blockEnabled() and not core:isOptionAutoProneAtk() then
        core:setOptionAutoProneAtk(true)
    end
end

function Controls.modifiersMatch()
    return not getCore():invalidBindingShiftCtrl(getCore():getKeyBinding(Controls.binding))
end

local function entryFor(name)
    for i, entry in ipairs(MainOptions.keyText) do
        if entry.txt and entry.txt:getName() == name then return entry, i end
    end
end

local function refreshCheckbox(self)
    local box = self.b42SMACheckbox
    if not box then return end
    local entry = entryFor(Controls.binding)
    local usable = Controls.usableKey(entry and entry.keyCode or Controls.key())
    box.enable = usable
    box:disableOption(box.options[1], not usable)
    if not usable then box:setSelected(1, false) end
    local option = self.gameOptions:get("autoProneAtk")
    if not option then return end
    local auto = option.control
    local locked = usable and box:isSelected(1)
    if locked then
        if not self.b42SMAAutoLocked then self.b42SMAPendingAuto = auto:isSelected(1) end
        auto:setSelected(1, true)
    elseif self.b42SMAAutoLocked and self.b42SMAPendingAuto ~= nil then
        auto:setSelected(1, self.b42SMAPendingAuto)
    end
    auto.enable = not locked
    auto:disableOption(auto.options[1], locked)
    self.b42SMAAutoLocked = locked
end

local function positionCheckbox(self)
    local box = self.b42SMACheckbox
    local entry = entryFor(Controls.binding)
    if box and entry then
        local panel = box.parent
        local spacing = MainOptions.style and MainOptions.style.borderSpacing or 10
        local columnWidth = MainOptions.keyBindingLength + spacing + self.keyButtonWidth + 100
        local rightColumn = (self:getWidth() - columnWidth * 2) / 2 + columnWidth
        box:setX(math.max(10, math.min(rightColumn, panel:getWidth() - box:getWidth() - 10)))
        box:setY(entry.btn:getY() + (entry.btn:getHeight() - box:getHeight()) / 2)
    end
end

local originalPanel = MainOptions.addKeybindingPanel
MainOptions.addKeybindingPanel = function(self, ...)
    -- Let Vanilla retain every original left/right slot. Add our paired row afterward.
    local keys, floorKey = MainOptions.keys, nil
    local nativeKeys = {}
    for _, key in ipairs(keys) do
        if key.value == Controls.binding then floorKey = key else nativeKeys[#nativeKeys + 1] = key end
    end
    MainOptions.keys = nativeKeys
    local ok, problem = pcall(originalPanel, self, ...)
    MainOptions.keys = keys
    if not ok then error(problem) end
    local attack = entryFor(KeybindId.ATTACK_CLICK:getId())
    local sneak, insertAfter = entryFor(KeybindId.CROUCH:getId())
    if not floorKey or not attack or not sneak then return end
    local panel = attack.btn.parent
    local spacing = MainOptions.style and MainOptions.style.borderSpacing or 10
    local height = math.max(attack.btn:getHeight(), getTextManager():getFontHeight(UIFont.Small) + 6)
    local rowY = math.max(attack.btn:getY() + attack.btn:getHeight(), sneak.btn:getY() + sneak.btn:getHeight()) + spacing
    local delta = height + spacing
    for _, child in pairs(panel:getChildren()) do
        if child ~= panel.vscroll and child ~= panel.hscroll and child:getY() >= rowY - 0.1 then
            child:setY(child:getY() + delta)
        end
    end
    panel:setScrollHeight(panel:getScrollHeight() + delta)
    local label = ISLabel:new(attack.btn:getX() - spacing, rowY, height, Controls.binding, 1, 1, 1, 1, UIFont.Small)
    label:initialise()
    label:setAnchorLeft(false); label:setAnchorRight(true)
    label:setTranslation(getText("UI_optionscreen_binding_" .. Controls.binding))
    panel:addChild(label)
    local button = ISButton:new(attack.btn:getX(), rowY, self.keyButtonWidth, height,
        MainOptions.getKeyPrefix(floorKey) .. getKeyName(tonumber(floorKey.key)), self, MainOptions.onKeyBindingBtnPress)
    button.internal = Controls.binding
    button:initialise(); button:instantiate()
    button:setTooltip(getText("IGUI_B42SMA_KeyTip"))
    panel:addChild(button)
    local entry = {txt=label, btn=button, keyCode=tonumber(floorKey.key) or 0, altCode=tonumber(floorKey.altCode) or 0,
        shift=floorKey.shift, ctrl=floorKey.ctrl, alt=floorKey.alt, left=true}
    table.insert(MainOptions.keyText, insertAfter + 1, entry)
    local box = ISTickBox:new(0, rowY, height, height, "B42SecMeleAttack")
    box:initialise()
    panel:addChild(box)
    box:addOption(getText("IGUI_B42SMA_Block"))
    box:setWidthToFit()
    box.choicesColor = {r=1, g=1, b=1, a=1}
    box.tooltip = getText("IGUI_B42SMA_BlockTip")
    box:setSelected(1, Controls.enabled)
    self.b42SMACheckbox = box
    local option = GameOption:new("B42SecMeleAttack.BlockPrimaryFloor", box)
    function option.toUI(optionSelf)
        optionSelf.control:setSelected(1, Controls.enabled)
        refreshCheckbox(self)
    end
    function option.onChange() refreshCheckbox(self) end
    function option.apply(optionSelf)
        if Controls.save(optionSelf.control:isSelected(1)) then
            -- Apply makes the forced-on value the current Vanilla setting.
            -- Later unlocking permits editing it rather than restoring stale UI state.
            self.b42SMAAutoLocked = false
            self.b42SMAPendingAuto = nil
            refreshCheckbox(self)
        end
    end
    if self.b42SMAOption then self.gameOptions:remove(self.b42SMAOption) end
    self.b42SMAOption = option
    self.gameOptions:add(option)
    positionCheckbox(self)
    refreshCheckbox(self)
end

local originalCenter = MainOptions.centerKeybindings
MainOptions.centerKeybindings = function(self, ...)
    originalCenter(self, ...)
    positionCheckbox(self)
end

local originalChanged = MainOptions.onKeybindChanged
MainOptions.onKeybindChanged = function(self, name, ...)
    if originalChanged then originalChanged(self, name, ...) end
    if name == Controls.binding then refreshCheckbox(self) end
end

Events.OnGameBoot.Add(Controls.ensureKeys)
return Controls
