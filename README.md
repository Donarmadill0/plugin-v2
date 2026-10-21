# GodVoice (Paper 1.21.11)

Server-only plugin. Players do not need a mod. (For voice lines they need the Simple Voice Chat mod, as usual.)

## Build WITHOUT installing anything (GitHub)
1. Make a free GitHub account, create a new repository, upload everything in this folder
   (make sure the hidden `.github` folder uploads too).
2. Open the repo's Actions tab -> the "build" run -> download the `godvoice-plugin` artifact.
3. Unzip it and put `godvoice-1.0.0.jar` in your server's `plugins` folder. Restart.

## Build locally
Needs Java 21 + Gradle:  gradle build   -> build/libs/godvoice-1.0.0.jar

## First-time setup
In game as op:   /god grant <yourname> #9B30FF The Void
Then:            /god            (shows every command)

## Commands
/god grant <player> <#hex> <title...>   op only, makes someone a god
/god revoke <player>                    op only
/god speak <player> <message...>        box appears on that player's screen only
/god broadcast <message...>             speak to all online followers
/god vanish                             invisible + particle aura
/god voice <player|followers|off>       private voice line (needs Simple Voice Chat on the server)
/god followers, /god release <player>
/vow swear <god>, /vow break <god>, /vow list

Box position/size/darkness: plugins/GodVoice/config.yml (box section).
/god model spawn|remove  places / removes your tall antlered form (block displays + particle beam).
