package com.continuum.nova.coremod;

import com.continuum.nova.NovaConstants;
import com.google.common.eventbus.EventBus;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.ModMetadata;

import java.util.Collections;

public class NovaForgeModContainer extends DummyModContainer {
    public NovaForgeModContainer() {
        super(new ModMetadata());
        ModMetadata meta = getMetadata();
        meta.version = NovaConstants.CORE_MOD_VERSION;
        meta.name = NovaConstants.CORE_MOD_NAME;
        meta.description = "Forge wrapper of the nova renderer. This is the coremod!";
        meta.modId = NovaConstants.CORE_MOD_ID;
        meta.credits = ""; // TODO: Fill in credits
        meta.authorList = Collections.emptyList(); // TODO: Fill in authors
    }

    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        bus.register(this);
        return true;
    }
}
