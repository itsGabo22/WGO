import React from "react";
import { Car, Footprints } from "lucide-react";
import { StatusBadge } from "./StatusBadge";
import { MassiveButton } from "./MassiveButton";
import { motion } from "framer-motion";

interface ScreenModeSelectionProps {
  destination?: string;
  onSelectMode: (mode: "drive" | "walk") => void;
}

export function ScreenModeSelection({ destination = "Centro Histórico", onSelectMode }: ScreenModeSelectionProps) {
  return (
    <motion.div 
      className="flex h-full w-full flex-col items-center py-6"
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.3 }}
    >
      {/* Top Zone */}
      <div className="flex flex-col items-center gap-1 mt-2">
        <StatusBadge variant="primary">VOICE ON</StatusBadge>
        <h1 className="mt-1 text-center text-[20px] font-bold leading-tight tracking-tight text-black px-4 truncate max-w-[280px]">
          {destination}
        </h1>
      </div>

      {/* Center Zone Actions */}
      <div className="flex-1 flex flex-col items-center justify-center gap-3 w-full px-6">
        <MassiveButton 
          variant="primary" 
          className="w-full text-[14px] tracking-widest"
          onClick={() => onSelectMode("drive")}
        >
          <Car size={20} strokeWidth={2.5} />
          CONDUCIR
        </MassiveButton>

        <MassiveButton 
          variant="inverted" 
          className="w-full text-[14px] tracking-widest"
          onClick={() => onSelectMode("walk")}
        >
          <Footprints size={20} strokeWidth={2.5} />
          CAMINAR
        </MassiveButton>
      </div>
    </motion.div>
  );
}
