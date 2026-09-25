Set WshShell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)
repoDir = fso.GetParentFolderName(scriptDir)
psScript = fso.BuildPath(scriptDir, "auto-git-push.ps1")

interval = 10
If WScript.Arguments.Count > 0 Then
    interval = WScript.Arguments(0)
End If

cmd = "powershell.exe -NoLogo -ExecutionPolicy Bypass -WindowStyle Hidden -File """ & psScript & """ -IntervalMinutes " & interval & " -Quiet"
WshShell.CurrentDirectory = repoDir
WshShell.Run cmd, 0, False
