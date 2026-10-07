"use client";

import React, { useEffect } from "react";
import { motion } from "framer-motion";
import { useTelemetry } from "@/hooks/useTelemetry";
import { NextTurnIndicator } from "./NextTurnIndicator";
import { AbstractMinimap } from "./AbstractMinimap";
import { BottomNavigationHUD } from "./BottomNavigationHUD";
import { X } from "lucide-react";

interface ScreenActiveNavigationProps {
  isActive: boolean;
  onCancel: () => void;
  onArrived: () => void;
}

export function ScreenActiveNavigation({ isActive, onCancel, onArrived }: ScreenActiveNavigationProps) {
  const { data: telemetry, isFinished } = useTelemetry(isActive);

  useEffect(() => {
    if (isFinished) {
      onArrived();
    }
  }, [isFinished, onArrived]);

  if (!telemetry) return null;

  return (
    <motion.div 
      className="flex h-full w-full flex-col items-center justify-between py-6 relative"
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.95 }}
      transition={{ duration: 0.3 }}
    >
      <button 
        onClick={onCancel}
        className="absolute top-4 right-4 z-50 flex h-[48px] w-[48px] items-center justify-center rounded-full bg-surface/80 border border-neutral/10 text-black active:scale-90 transition-transform"
        aria-label="Cancelar navegación"
      >
        <X size={20} strokeWidth={2.5} />
      </button>

      <div className="w-full flex-1 flex flex-col items-center pt-2 gap-4">
        <NextTurnIndicator 
          instruction={telemetry.instruction}
          distance={telemetry.distanceToTurn}
          turnDirection={telemetry.turnDirection}
        />
        <AbstractMinimap />
      </div>

      <div className="w-full mb-2">
        <BottomNavigationHUD 
          eta={telemetry.eta}
          totalDistance={telemetry.totalDistance}
        />
      </div>
    </motion.div>
  );
}
