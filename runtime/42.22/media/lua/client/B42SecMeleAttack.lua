local Controls = require "B42SecMeleAttack/Controls"

local function sync()
    if B42SecMeleAttack then
        Controls.enforceAutoProne()
        B42SecMeleAttack.setBlockPrimaryFloor(Controls.blockEnabled())
    end
end

local trackedMouseKey, mouseWasDown = 0, false
local function keyPressed(key, fromMouse)
    if key == 0 or key ~= Controls.key() or not B42SecMeleAttack then return end
    if not Controls.blockEnabled() or not Controls.modifiersMatch() or (key >= 10000 and not fromMouse) then return end
    if isGamePaused() or getCore():isDoingTextEntry() then return end
    if MainOptions and MainOptions.instance and MainOptions.instance:isVisible() then return end
    if MainScreen and MainScreen.instance and MainScreen.instance:isVisible() then return end
    if UIManager and UIManager.isModalVisible() then return end
    local player = getSpecificPlayer(0)
    if not player then return end
    local radial = getPlayerRadialMenu(0)
    if radial and radial:isReallyVisible() then return end
    sync()
    B42SecMeleAttack.attack(player)
end

local function update(player)
    if player and player:getPlayerNum() == 0 and B42SecMeleAttack then
        sync()
        B42SecMeleAttack.update(player)
        local key = Controls.key()
        local down = B42SecMeleAttack.mouseBindingDown(key)
        -- A rebind or held button when entering the world is not a fresh press.
        if trackedMouseKey ~= key then
            trackedMouseKey, mouseWasDown = key, down
            return
        end
        local pressed = down and not mouseWasDown
        mouseWasDown = down
        if pressed and B42SecMeleAttack.mouseBindingAvailable(key) then keyPressed(key, true) end
    end
end

Events.OnKeyStartPressed.Add(keyPressed)
Events.OnPlayerUpdate.Add(update)
Events.OnGameStart.Add(function()
    if B42SecMeleAttack then B42SecMeleAttack.reset() end
    Controls.ensureKeys()
    trackedMouseKey = Controls.key()
    mouseWasDown = B42SecMeleAttack and B42SecMeleAttack.mouseBindingDown(trackedMouseKey) or false
    sync()
end)
