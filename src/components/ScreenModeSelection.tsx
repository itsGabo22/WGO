import React from "react";
import { Car, Footprints } from "lucide-react";
import { StatusBadge } from "./StatusBadge";
import { MassiveButton } from "./MassiveButton";

interface ScreenModeSelectionProps {
  destination?: string;
  onSelectMode: (mode: "drive" | "walk") => void;
}

export function ScreenModeSelection({ destination = "Centro Histórico", onSelectMode }: ScreenModeSelectionProps) {
  return (
    <div className="flex h-full w-full flex-col justify-between py-10 px-6">
      {/* Top Zone */}
      <div className="flex flex-col items-center gap-2">
        <h1 className="text-center text-[22px] font-bold leading-tight tracking-tight text-black truncate max-w-[280px]">
          {destination}
        </h1>
        <StatusBadge variant="primary">VOICE ON</StatusBadge>
      </div>

      {/* Action Zone */}
      <div className="flex flex-col items-center justify-end gap-3 w-full pb-2">
        <MassiveButton 
          variant="primary" 
          className="w-full text-[15px] tracking-widest"
          onClick={() => onSelectMode("drive")}
        >
          <Car size={22} strokeWidth={2.5} />
          CONDUCIR
        </MassiveButton>

        <MassiveButton 
          variant="inverted" 
          className="w-full text-[15px] tracking-widest"
          onClick={() => onSelectMode("walk")}
        >
          <Footprints size={22} strokeWidth={2.5} />
          CAMINAR
        </MassiveButton>
      </div>
    </div>
  );
}
