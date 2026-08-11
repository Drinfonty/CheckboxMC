# Release Notes - Version 1.0.0

The first release of Checkbox: a todo list that lives on your HUD and ticks itself off as you
play. Write down what you meant to do, and let the mod count the logs and the zombies for you.

Checkbox is **client-side only**. It works on vanilla servers, and nothing needs to be
installed server-side.

## What you can track

- **Text entries** — anything at all. "Build a house". Tick it off yourself when it's done.
- **Item counters** — "Collect 8 Oak Log". Progress goes up as you gather, whether the item
  came off a tree, out of a chest, or from a crafting grid.
- **Kill counters** — "Kill 10 Zombie". Counts mobs you killed, including with a bow or a
  splash potion.
- **Timers** — a countdown that chimes when it runs out. Handy for minigames
  such as hide and seek.

Counters name themselves from what you're tracking, so there's no description to write. Change
the target from 8 to 16 and the entry relabels itself.

## Making a list

Press **`=`** to open the list manager. From there you can add, edit, reorder and delete
entries, or clear out everything you've finished.

When you add an item or kill counter, you can type the id with autocomplete, click **`...`** to
browse every item in a grid laid out like the creative inventory, or use **Use held item** /
**Use looked-at** to grab whatever you're holding or looking at. Only things you can actually
collect or kill are offered — no spawn eggs, command blocks or minecarts cluttering the list.

## The HUD

Your list is drawn on screen with a checkbox per entry, a progress count, a small bar, and the
item or mob it's tracking. Finished entries turn green.

Everything about it is adjustable in **HUD Settings**: nine anchor positions, scale, width, how
many rows to show, background style and opacity, text shadow, and whether completed entries
fade away or stay. **Move HUD…** lets you drag it where you want, with the arrow keys for
fine adjustment. Positions are stored relative to the corner you pick, so they survive changing
your resolution or GUI scale.

Press **`J`** to show or hide the HUD at any time, or use the button in the list manager. It
also hides with F1, like the rest of the interface.

## Lists per world

Each world and each server gets its own list, so your Nether-trip checklist doesn't follow you
into someone else's server. Entries you want everywhere can be set to **Global** when you
create or edit them.

## When something finishes

Completing a counter plays a light chime and shows a toast in the corner; a timer running out
plays a louder one, and its row blinks until you deal with it. Both sounds and toasts can be
turned off in HUD Settings.

## Worth knowing

- Kills are only counted if you did the damage, and only for mobs that die where your game can
  see them. A skeleton that falls to its death in an unloaded chunk after you shot it won't
  count.
- Item counters default to counting anything you **acquire**, so progress never goes backwards.
  Dropping items and picking them up again counts twice. If you'd rather an entry showed what
  you're carrying right now, switch it to **held right now** when you create it.
- Progress is stored on your computer, not on the server, so it doesn't sync between devices or
  with other players.

## Platform Compatibility

This release compiles and runs on:
- **Minecraft 26.2** (Fabric & NeoForge)

Fabric users need Fabric API. On Fabric, Checkbox also appears in ModMenu; on NeoForge, use the
Config button in the mods list.
